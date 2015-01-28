/*
 * Copyright 2015 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.fix_gateway.dictionary.ir;

import java.util.List;

public class DataDictionary
{
    private final List<Message> messages;
    private final List<Field> fields;

    public DataDictionary(final List<Message> messages, final List<Field> fields)
    {
        this.messages = messages;
        this.fields = fields;
    }

    public List<Message> messages()
    {
        return messages;
    }

    public List<Field> fields()
    {
        return fields;
    }

    @Override
    public String toString()
    {
        return "DataDictionary{" +
                "messages=" + messages +
                ", fields=" + fields +
                '}';
    }
}
