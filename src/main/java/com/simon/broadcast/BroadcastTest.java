package com.simon.broadcast;


import java.io.*;
import java.net.*;
import java.lang.*;
import java.util.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class BroadcastTest
{
    public static UUID id = null;
    public static LinkedList<Broadcaster> bcasters = new LinkedList<Broadcaster>();
    public static LinkedList<Integer> netIFaceIDs = new LinkedList<Integer>();

//     public static String BCAST_ADDRESS = "239.195.188.235";
//     public static int BASE_AMP_BCAST_PORT = 17089;
//     public static int AMP_BCAST_PORT_OFFSET = 120;

    public static String BCAST_ADDRESS = "224.0.113.0";
    public static int BASE_AMP_BCAST_PORT = 4446;
    public static int AMP_BCAST_PORT_OFFSET = 0;

    public static void main(String[] args) throws IOException
    {
        id = UUID.randomUUID();
        System.out.printf("COMMS ID: %s\r\n", id);
        if (args.length == 1) {
            String arg1 = args[0];

            try
            {
                AMP_BCAST_PORT_OFFSET = Integer.valueOf(arg1);
            } catch (Exception ex) {}
        }

        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface iface : Collections.list(ifaces))
        {
            if (Collections.list(iface.getInetAddresses()).get(0).toString().contains("127.0.0.1"))
            {
                continue;
            }

             System.out.printf("NetworkInterface %s hashCode: %d\r\n", iface.getDisplayName(), iface.hashCode());
             netIFaceIDs.add(iface.hashCode());

            Broadcaster broadcaster = broadcasterForInterface(iface);
            bcasters.add(broadcaster);

            Listener listener = listenerForInterface(iface);
            listener.setBroadcaster(broadcaster);
        }

        Message announce = new Message(id, "announce");
        synchronized(bcasters)
        {
            for(Broadcaster b : bcasters)
            {
                b.add(announce);
            }
        }



            // displayInterfaceInformation(netint);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        String input;
        while((input=br.readLine())!=null){
            if (input.length() > 0)
            {
                Message msg = new Message(id, input);
                synchronized(bcasters)
                {
                    for(Broadcaster b : bcasters)
                    {
                        b.add(msg);
                    }
                }
            }
        }
    }

    static void displayInterfaceInformation(NetworkInterface netint) throws SocketException {
        System.out.printf("Display name: %s\n", netint.getDisplayName());
        System.out.printf("Name: %s\n", netint.getName());
        Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
        for (InetAddress inetAddress : Collections.list(inetAddresses)) {
            System.out.printf("InetAddress: %s\n", inetAddress);
        }
        System.out.printf("\n");
     }

    static Broadcaster broadcasterForInterface(NetworkInterface iface)
    {
        Broadcaster bcaster = new Broadcaster(iface);
        Thread b = new Thread(bcaster);
        b.start();
        return bcaster;
    }

    static Listener listenerForInterface(NetworkInterface iface)
    {
        Listener listen = new Listener(iface);
        Thread l = new Thread(listen);
        l.start();
        return listen;
    }

    private static class Broadcaster implements Runnable
    {
        private NetworkInterface iface = null;
        private InetAddress ip = null;
        private LinkedList<Message> messages = new LinkedList<Message>();
        private Gson gson = new Gson();
        public Broadcaster(NetworkInterface iface)
        {
            this.iface = iface;
            this.ip = Collections.list(iface.getInetAddresses()).get(0);
        }

        public void add(Message msg)
        {
            synchronized(messages)
            {
                messages.add(msg);
            }
        }

        @Override
        public void run()
        {
            try {
                MulticastSocket socket = new MulticastSocket(BASE_AMP_BCAST_PORT + AMP_BCAST_PORT_OFFSET);
                socket.setNetworkInterface(iface);
                // socket.setLoopbackMode(true);
                InetAddress group = InetAddress.getByName(BCAST_ADDRESS);
                socket.joinGroup(group);

                while (true) {
                    Thread.sleep(1);
                    byte[] buf = new byte[256];

                    Message msg = null;
                    synchronized(messages)
                    {
                        if (messages.size() > 0)
                        {
                            msg = messages.removeFirst();
                        }
                    }

                    if (msg != null)
                    {
                        buf = gson.toJson(msg).getBytes();
                        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, BASE_AMP_BCAST_PORT + AMP_BCAST_PORT_OFFSET);
                        socket.send(packet);
                    }
                }
            } catch (IOException ioex){}catch(InterruptedException iex){}
        }
    }

    private static class Listener implements Runnable
    {
        private Broadcaster broadcaster = null;
        private NetworkInterface iface = null;
        private InetAddress ip = null;
        private Gson gson = new Gson();
        public Listener(NetworkInterface iface)
        {
            this.iface = iface;
            this.ip = Collections.list(iface.getInetAddresses()).get(0);
        }

        public void setBroadcaster(Broadcaster b)
        {
            this.broadcaster = b;
        }

        @Override
        public void run()
        {
            if (this.ip.toString().contains("127.0.0.1"))
            {
                Thread.yield();
                return;
            }
            try {
                MulticastSocket socket = new MulticastSocket(BASE_AMP_BCAST_PORT + AMP_BCAST_PORT_OFFSET);
                socket.setNetworkInterface(iface);
                InetAddress group = InetAddress.getByName(BCAST_ADDRESS);
                socket.joinGroup(group);

                while (true) {
                    Thread.sleep(1);
                    byte[] buf = new byte[256];

                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    String received = new String(packet.getData()).trim();
                    Message msg = gson.fromJson(received, Message.class);

                    if (msg.source.equals(id) == false)
                    {
                        System.out.printf("%s ==> %s\r\n", msg.source, msg.msg);
                        ProcessResponse(msg.msg);

                        // if (msg.msg.contains("ACK") == false)
                        // {
                        //
                        // }
                    }
                }
            } catch (IOException ioex){}catch(InterruptedException iex){}
        }

        private void ProcessResponse(String msg)
        {
            String rVal = "NACK";

            String[] splits = msg.trim().split(" ");
            String targetMethod = splits[0];

            if (targetMethod.equalsIgnoreCase("ACK") || targetMethod.equalsIgnoreCase("NACK"))
            {
                return;
            }

            Method[] methods = this.getClass().getDeclaredMethods();
            for (Method m : methods)
            {
                String mname = m.getName();
                if (mname.equals(targetMethod) == false)
                {
                    continue;
                }

                try
                {
                    m.setAccessible(true);
                    String[] args = Arrays.copyOfRange(splits, 1, splits.length);
                    Object o = m.invoke(this, (Object)args);
                    if (o instanceof Message)
                    {
                        this.broadcaster.add((Message) o);
                        return;
                    }
                    else
                    {
                        rVal = String.valueOf(o);
                        this.broadcaster.add(new Message(id, "ACK " + rVal));
                    }

                    return;
                }
                catch (InvocationTargetException x) { x.printStackTrace(); }
                catch (IllegalAccessException x) { x.printStackTrace(); }
            }

            this.broadcaster.add(new Message(id, "ACK NACK " + targetMethod));
            return;
        }


        public int add(String... args)
        {
            int rVal = 0;

            for (String arg : args)
            {
                rVal = rVal + Integer.valueOf(arg);
            }

            return rVal;
        }

        public Message announce(String... args)
        {
            return new Message(id, "ACK ANNOUNCE");
        }
    }

    private static class Message
    {
        public UUID source = null;
        public UUID id = null;
        public UUID responseTo = null;
        public String msg = "";
        public Message(UUID source, String toSend)
        {
            this(source, toSend, null);
        }

        public Message(UUID source, String toSend, UUID respondingTo)
        {
            this.id = UUID.randomUUID();
            this.source = source;
            this.msg = toSend;
            this.responseTo = respondingTo;
        }
    }
}
