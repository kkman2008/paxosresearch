package paxos;

import org.junit.Test;
import paxos.communication.CommLayer;
import paxos.communication.Member;
import paxos.communication.Tick;
import paxos.messages.MessageWithSender;

import java.io.Serializable;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static paxos.TestUtils.createMembership;

public class MultiRequestTest {

    public static final byte[] HELLO_BYTES = PaxosUtils.serialize("hello");

    @Test
    public void testSendingAndReplying() throws Exception {
        List<Member> members = TestUtils.createMembersOnLocalhost(3);
        GroupMembership membership = createMembership(members, 0);
        CommLayer messenger = mock(CommLayer.class);
        final boolean[] quorumReached = new boolean[] {false};
        final boolean[] completed = new boolean[] {false};

        MultiRequest<String, DummyResponse> multiRequest = new MultiRequest<String, DummyResponse>(membership, messenger, "hello", 0) {
            @Override protected void onQuorumReached() {
                quorumReached[0] = true;
            }

            @Override protected void onCompleted() {
                completed[0] = true;
            }
        };

        verify(messenger).sendTo(members, HELLO_BYTES);

        multiRequest.receive(new Tick(1001));

        verify(messenger).sendTo(members.get(0), HELLO_BYTES);
        verify(messenger).sendTo(members.get(1), HELLO_BYTES);
        verify(messenger).sendTo(members.get(2), HELLO_BYTES);

        multiRequest.receive(new DummyResponse("yes", members.get(0)));
        assertFalse(quorumReached[0]);
        assertFalse(completed[0]);
        assertFalse(multiRequest.isFinished());

        multiRequest.receive(new DummyResponse("yes", members.get(1)));
        assertTrue(quorumReached[0]);
        assertFalse(completed[0]);
        assertFalse(multiRequest.isFinished());

        multiRequest.receive(new DummyResponse("yes", members.get(2)));
        assertTrue(quorumReached[0]);
        assertTrue(completed[0]);
        assertFalse(multiRequest.isFinished());

        verifyNoMoreInteractions((CommLayer) messenger);
    }

    @Test
    public void testFilteringWrongMessages() throws Exception {
        List<Member> members = TestUtils.createMembersOnLocalhost(3);
        GroupMembership membership = createMembership(members, 0);
        CommLayer messenger = mock(CommLayer.class);
        final boolean[] quorumReached = new boolean[] {false};
        final boolean[] completed = new boolean[] {false};

        MultiRequest<String, DummyResponse> multiRequest = new MultiRequest<String, DummyResponse>(membership, messenger, "hello", 0) {
            @Override
            protected DummyResponse filterResponse(Serializable message) {
                return (message instanceof DummyResponse) ? (DummyResponse) message : null;
            }

            @Override protected void onQuorumReached() {
                quorumReached[0] = true;
            }

            @Override protected void onCompleted() {
                completed[0] = true;
            }
        };

        verify(messenger).sendTo(members, HELLO_BYTES);

        multiRequest.receive(new Tick(1000));

        multiRequest.receive(new WrongResponse("yes", members.get(0)));
        multiRequest.receive(new WrongResponse("yes", members.get(1)));
        multiRequest.receive(new WrongResponse("yes", members.get(2)));

        assertFalse(quorumReached[0]);
        assertFalse(completed[0]);
        assertFalse(multiRequest.isFinished());
    }

    @Test
    public void testResending() throws Exception {
        List<Member> members = TestUtils.createMembersOnLocalhost(3);
        GroupMembership membership = createMembership(members, 0);
        CommLayer messenger = mock(CommLayer.class);
        final boolean[] quorumReached = new boolean[] {false};
        final boolean[] completed = new boolean[] {false};

        MultiRequest<String, DummyResponse> multiRequest = new MultiRequest<String, DummyResponse>(membership, messenger, "hello", 0) {
            @Override protected void onQuorumReached() {
                quorumReached[0] = true;
            }

            @Override protected void onCompleted() {
                completed[0] = true;
            }
        };

        verify(messenger).sendTo(members, HELLO_BYTES);

        multiRequest.receive(new Tick(1001));

        verify(messenger).sendTo(members.get(0), HELLO_BYTES);
        verify(messenger).sendTo(members.get(1), HELLO_BYTES);
        verify(messenger).sendTo(members.get(2), HELLO_BYTES);

        multiRequest.receive(new DummyResponse("yes", members.get(0)));
        assertFalse(quorumReached[0]);
        assertFalse(completed[0]);
        assertFalse(multiRequest.isFinished());

        verifyNoMoreInteractions((CommLayer) messenger);
        multiRequest.tick(2000);
        verify(messenger).sendTo(members.get(1), HELLO_BYTES);
        verify(messenger).sendTo(members.get(2), HELLO_BYTES);

        multiRequest.receive(new DummyResponse("yes", members.get(1)));
        assertTrue(quorumReached[0]);
        assertFalse(completed[0]);
        assertFalse(multiRequest.isFinished());

        verifyNoMoreInteractions((CommLayer) messenger);
        multiRequest.tick(4000);
        verify(messenger, times(2)).sendTo(members.get(2), HELLO_BYTES);

        multiRequest.receive(new DummyResponse("yes", members.get(2)));
        assertTrue(quorumReached[0]);
        assertTrue(completed[0]);
        assertFalse(multiRequest.isFinished());

        multiRequest.tick(6000);

        verifyNoMoreInteractions((CommLayer) messenger);
    }

    private static class DummyResponse implements MessageWithSender {
        public String content;
        public Member sender;

        public DummyResponse(String content, Member sender) {
            this.content = content;
            this.sender = sender;
        }

        public Member getSender() { return sender; }
    }

    private static class WrongResponse implements MessageWithSender {
        public String content;
        public Member sender;

        public WrongResponse(String content, Member sender) {
            this.content = content;
            this.sender = sender;
        }

        public Member getSender() { return sender; }
    }

}