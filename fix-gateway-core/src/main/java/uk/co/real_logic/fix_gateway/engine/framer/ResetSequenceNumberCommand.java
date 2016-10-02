/*
 * Copyright 2015-2016 Real Logic Ltd.
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
package uk.co.real_logic.fix_gateway.engine.framer;

import uk.co.real_logic.fix_gateway.Pressure;
import uk.co.real_logic.fix_gateway.Reply;
import uk.co.real_logic.fix_gateway.engine.logger.SequenceNumberIndexReader;
import uk.co.real_logic.fix_gateway.protocol.GatewayPublication;
import uk.co.real_logic.fix_gateway.session.Session;

import static uk.co.real_logic.fix_gateway.Reply.State.COMPLETED;
import static uk.co.real_logic.fix_gateway.Reply.State.ERRORED;
import static uk.co.real_logic.fix_gateway.engine.SessionInfo.UNK_SESSION;

class ResetSequenceNumberCommand implements Reply<Void>, AdminCommand
{

    private volatile State state = State.EXECUTING;
    // write to error only when updating state
    private Exception error;

    private final long sessionId;

    // State to only be accessed on the Framer thread.
    private final GatewaySessions gatewaySessions;
    private final SequenceNumberIndexReader receivedSequenceNumberIndex;
    private final SequenceNumberIndexReader sentSequenceNumberIndex;
    private final GatewayPublication inboundPublication;
    private final GatewayPublication outboundPublication;
    private Session session;

    private enum Step
    {
        START,
        RESET_SESSION,
        RESET_SENT,
        RESET_RECV,
        AWAIT_RECV,
        AWAIT_SENT,
        DONE
    }
    private Step step = Step.START;

    ResetSequenceNumberCommand(
        final long sessionId,
        final GatewaySessions gatewaySessions,
        final SequenceNumberIndexReader receivedSequenceNumberIndex,
        final SequenceNumberIndexReader sentSequenceNumberIndex,
        final GatewayPublication inboundPublication,
        final GatewayPublication outboundPublication)
    {
        this.sessionId = sessionId;
        this.gatewaySessions = gatewaySessions;
        this.receivedSequenceNumberIndex = receivedSequenceNumberIndex;
        this.sentSequenceNumberIndex = sentSequenceNumberIndex;
        this.inboundPublication = inboundPublication;
        this.outboundPublication = outboundPublication;
    }

    public Exception error()
    {
        return error;
    }

    private void onError(final Exception error)
    {
        this.error = error;
        state = ERRORED;
    }

    public Void resultIfPresent()
    {
        return null;
    }

    public State state()
    {
        return state;
    }

    public void execute(final Framer framer)
    {
        framer.onResetSequenceNumber(this);
    }

    // Only to be called on the Framer thread.
    boolean poll()
    {
        switch (step)
        {
            case START:
            {
                if (sessionIsUnknown())
                {
                    onError(new IllegalArgumentException(
                        String.format("Unknown sessionId: %d", sessionId)));

                    return true;
                }

                final GatewaySession gatewaySession = gatewaySessions.sessionById(sessionId);
                if (gatewaySession != null)
                {
                    session = gatewaySession.session();
                    step = Step.RESET_SESSION;
                }
                else
                {
                    step = Step.RESET_RECV;
                }

                return false;
            }

            // TODO: what if session is acquired part way through this action?

            case RESET_SESSION:
            {
                final long position = session.resetSequenceNumbers();
                if (!Pressure.isBackPressured(position))
                {
                    step = Step.AWAIT_RECV;
                }
                return false;
            }

            case RESET_RECV:
                return reset(inboundPublication, Step.RESET_SENT);

            case RESET_SENT:
                return reset(outboundPublication, Step.AWAIT_RECV);

            case AWAIT_RECV:
                return await(receivedSequenceNumberIndex);

            case AWAIT_SENT:
                return await(sentSequenceNumberIndex);

            case DONE:
                return true;
        }

        return false;
    }

    private boolean reset(final GatewayPublication publication, final Step nextStep)
    {
        if (!Pressure.isBackPressured(publication.saveResetSequenceNumber(sessionId)))
        {
            step = nextStep;
        }

        return false;
    }

    private boolean await(final SequenceNumberIndexReader sequenceNumberIndex)
    {
        final boolean done = sequenceNumberIndex.lastKnownSequenceNumber(sessionId) == 1;
        if (done)
        {
            step = Step.DONE;
            state = COMPLETED;
        }
        return done;
    }

    private boolean sessionIsUnknown()
    {
        return sentSequenceNumberIndex.lastKnownSequenceNumber(sessionId) == UNK_SESSION;
    }

    public String toString()
    {
        return "ResetSequenceNumberReply{" +
            "state=" + state +
            ", error=" + error +
            ", sessionId=" + sessionId +
            ", step=" + step +
            '}';
    }
}