#Paxos
##Lamport的Paxos算法的Java实现。

###为什么另一个Paxos实施？
这是完全有序的原子广播协议的实现。使用的算法是Lamport的Paxos的变种。该库是Apache Zookeeper的轻量级替代品。Zookeeper使用Paxos来保持副本之间的一致性，但客户端是远程的。这对Zookeeper的使用方式提出了几个限制。我们的库在VM中使用，客户端可以使用它来构建具有强一致性保证的复制，无论状态如何表示。

###为何选择Paxos？
原始算法要求保持状态。这显着降低了性能，但是使成员能够恢复是必需的。我们的解决方案不支持成员的恢复，而是支持更改组的成员。这使我们可以避免持久性并具有更高的吞吐量。

#如何使用它？
##基本组
###这是基本的组实现

警告：BasicGroup有一些限制，您应该使用动态组。
```java
        // this is the list of members
        Members members = new Members(
                new Member(), // this is a reference to a member on the localhost on default port (2440)
                new Member(2441), // this one is on localhost with the specified port
                new Member(InetAddress.getLocalHost(), 2442)); // you can specify the address and port manually

        // we need to define a receiver
        class MyReceiver implements Receiver {
            // we follow a reactive pattern here
            public void receive(Serializable message) {
                System.out.println("received " + message.toString());
            }
        };

        // this actually creates the members
        BasicGroup group1 = new BasicGroup(members.get(0), new MyReceiver());
        BasicGroup group2 = new BasicGroup(members.get(1), new MyReceiver());
        BasicGroup group3 = new BasicGroup(members.get(2), new MyReceiver());

        // this will cause all receivers to print "received Hello"
        group2.broadcast("Hello");

        Thread.sleep(1); // allow the members to receive the message

        group1.close(); group2.close(); group3.close();
```

###碎片组
BasicGroup有一个很大的限制：它不支持大于UDP数据包的消息。即使您从不发送大型消息，广播协议也可能在内部使用大型消息来同步状态。FragmentingGroup实现处理大型消息。此实现具有较小的开销（吞吐量降低约10％），但支持任何大小的消息。

###动态组
正如我们之前所说，我们的Paxos实施不支持成员的恢复。相反，我们支持向该组添加新成员。为了利用这一点，您必须使用DynamicGroup实现。加入时的状态转移留给用户，但我们保证每个新成员都收到消息的连续子序列。
