/*
 * Copyright 2015-2018 Real Logic Ltd, Adaptive Financial Consulting Ltd.
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
package uk.co.real_logic.artio.engine.logger;

import org.agrona.collections.Long2LongHashMap;
import org.agrona.concurrent.IdleStrategy;

import static io.aeron.archive.status.RecordingPos.NULL_RECORDING_ID;

public class RecordingIdLookup
{
    private final Long2LongHashMap aeronSessionIdToRecordingId = new Long2LongHashMap(NULL_RECORDING_ID);
    private final int requiredStreamId;
    private final RecordingIdStore recordingIdStore;
    private final IdleStrategy archiverIdleStrategy;

    void onStart(final long recordingId, final int sessionId, final int streamId)
    {
        if (streamId == requiredStreamId)
        {
            aeronSessionIdToRecordingId.put(sessionId, recordingId);
        }
    }

    RecordingIdLookup(
        final int requiredStreamId,
        final RecordingIdStore recordingIdStore,
        final IdleStrategy archiverIdleStrategy)
    {
        this.requiredStreamId = requiredStreamId;
        this.recordingIdStore = recordingIdStore;
        this.archiverIdleStrategy = archiverIdleStrategy;
    }

    long getRecordingId(final int aeronSessionId)
    {
        while (true)
        {
            final long recordingId = aeronSessionIdToRecordingId.get(aeronSessionId);
            if (recordingId == NULL_RECORDING_ID)
            {
                poll();
            }
            else
            {
                archiverIdleStrategy.reset();
                return recordingId;
            }

            archiverIdleStrategy.idle();
        }
    }

    int poll()
    {
        return recordingIdStore.poll();
    }
}
