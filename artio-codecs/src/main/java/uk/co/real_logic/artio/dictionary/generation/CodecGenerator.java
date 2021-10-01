/*
 * Copyright 2020 Adaptive Financial Consulting Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.artio.dictionary.generation;

import org.agrona.generation.OutputManager;
import org.agrona.generation.PackageOutputManager;
import uk.co.real_logic.artio.builder.RejectUnknownEnumValue;
import uk.co.real_logic.artio.builder.RejectUnknownField;
import uk.co.real_logic.artio.builder.Validation;
import uk.co.real_logic.artio.dictionary.DictionaryParser;
import uk.co.real_logic.artio.dictionary.ir.Dictionary;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public final class CodecGenerator
{
    public static void generate(final CodecConfiguration configuration) throws Exception
    {
        configuration.conclude();
        final InputStream[] fileStreams = configuration.fileStreams();

        try
        {
            final String outputPath = configuration.outputPath();
            final boolean allowDuplicates = configuration.allowDuplicateFields();
            final DictionaryParser parser = new DictionaryParser(allowDuplicates);
            final String codecRejectUnknownEnumValueEnabled = configuration.codecRejectUnknownEnumValueEnabled();

            final boolean sharedCodecs = configuration.sharedCodecsEnabled();
            if (sharedCodecs && fileStreams.length > 1)
            {
                generateSharedDictionaries(
                    configuration, fileStreams, outputPath, parser, codecRejectUnknownEnumValueEnabled);
            }
            else
            {
                generateNormalDictionaries(
                    configuration, fileStreams, outputPath, parser, codecRejectUnknownEnumValueEnabled);
            }
        }
        finally
        {
            Exceptions.closeAll(fileStreams);
        }
    }

    private static void generateSharedDictionaries(
        final CodecConfiguration configuration,
        final InputStream[] fileStreams,
        final String outputPath,
        final DictionaryParser parser,
        final String codecRejectUnknownEnumValueEnabled) throws Exception
    {
        final String[] dictionaryNames = configuration.dictionaryNames();
        final List<Dictionary> inputDictionaries = new ArrayList<>();
        for (int i = 0, fileStreamsLength = fileStreams.length; i < fileStreamsLength; i++)
        {
            final InputStream fileStream = fileStreams[i];
            final String name = normalise(dictionaryNames[i]);
            final Dictionary dictionary;
            try
            {
                dictionary = parser.parse(fileStream, null);
            }
            catch (final Exception e)
            {
                throw new IllegalArgumentException("Unable to parse: " + name, e);
            }
            dictionary.name(name);
            inputDictionaries.add(dictionary);
        }

        new CodecSharer(inputDictionaries).share();

        inputDictionaries.forEach(dictionary ->
            generateDictionary(configuration, outputPath, codecRejectUnknownEnumValueEnabled, dictionary));
    }

    private static String normalise(final String dictionaryName)
    {
        return dictionaryName.replace('.', '_');
    }

    private static void generateNormalDictionaries(
        final CodecConfiguration configuration,
        final InputStream[] fileStreams,
        final String outputPath,
        final DictionaryParser parser,
        final String codecRejectUnknownEnumValueEnabled) throws Exception
    {
        Dictionary dictionary = null;

        for (final InputStream fileStream : fileStreams)
        {
            dictionary = parser.parse(fileStream, dictionary);
        }

        generateDictionary(configuration, outputPath, codecRejectUnknownEnumValueEnabled, dictionary);
    }

    private static void generateDictionary(
        final CodecConfiguration configuration,
        final String outputPath,
        final String codecRejectUnknownEnumValueEnabled,
        final Dictionary dictionary)
    {
        String parentPackage = configuration.parentPackage();
        final String name = dictionary.name();
        if (name != null)
        {
            parentPackage += "." + name;
        }

        final String encoderPackage = parentPackage + ".builder";
        final String decoderPackage = parentPackage + ".decoder";
        final String decoderFlyweightPackage = parentPackage + ".decoder_flyweight";

        final BiFunction<String, String, OutputManager> outputManagerFactory =
            configuration.outputManagerFactory();
        final OutputManager parentOutput = outputManagerFactory.apply(outputPath, parentPackage);
        final OutputManager decoderOutput = outputManagerFactory.apply(outputPath, decoderPackage);
        final OutputManager encoderOutput = outputManagerFactory.apply(outputPath, encoderPackage);

        new EnumGenerator(dictionary, parentPackage, parentOutput).generate();
        new ConstantGenerator(dictionary, parentPackage, parentOutput).generate();

        new FixDictionaryGenerator(
            dictionary,
            parentOutput,
            encoderPackage,
            decoderPackage,
            parentPackage).generate();

        new EncoderGenerator(
            dictionary,
            encoderPackage,
            parentPackage,
            encoderOutput,
            Validation.class,
            RejectUnknownField.class,
            RejectUnknownEnumValue.class,
            codecRejectUnknownEnumValueEnabled).generate();

        new DecoderGenerator(
            dictionary,
            1,
            decoderPackage,
            parentPackage,
            encoderPackage,
            decoderOutput,
            Validation.class,
            RejectUnknownField.class,
            RejectUnknownEnumValue.class,
            false,
            codecRejectUnknownEnumValueEnabled).generate();

        new PrinterGenerator(dictionary, decoderPackage, decoderOutput).generate();
        new AcceptorGenerator(dictionary, decoderPackage, decoderOutput).generate();

        if (configuration.flyweightsEnabled())
        {
            final PackageOutputManager flyweightDecoderOutput =
                new PackageOutputManager(outputPath, decoderFlyweightPackage);

            new DecoderGenerator(
                dictionary,
                1,
                decoderFlyweightPackage,
                parentPackage,
                encoderPackage, flyweightDecoderOutput,
                Validation.class,
                RejectUnknownField.class,
                RejectUnknownEnumValue.class,
                true,
                codecRejectUnknownEnumValueEnabled).generate();
        }
    }
}
