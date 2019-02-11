package conn;

import time.ScalarClock;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

public class Conn {
  private ConcurrentHashMap<Integer, Sender> senderMap;
  private ConcurrentLinkedQueue<Message> messageQueue;
  private int nodeId;
  private ScalarClock time;

  private Semaphore sema;

  public Conn(int nodeId, int port, ScalarClock time,
              ConcurrentLinkedQueue<Message> messageQueue,
              Semaphore sema) {
    this.nodeId = nodeId;
    this.senderMap = new ConcurrentHashMap<>();
    this.messageQueue = messageQueue;
    this.time = time;
    this.sema = sema;
    new Thread(new Listener(port)).start();
  }

  private class Listener implements Runnable {
    private int port;

    private Listener(int port) {
      this.port = port;
    }

    @Override
    public void run() {
      try (ServerSocket listener = new ServerSocket(this.port)) {
        while (true) {
          Socket socket = listener.accept();
          ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
          ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
          Message message = (Message) inputStream.readObject();

          Sender sender = new Sender(outputStream);
          Thread senderThread = new Thread(sender);
          senderThread.start();
          senderMap.put(message.getSenderId(), sender);

          send(message.getSenderId(), new Message(nodeId, Message.Type.INI, time.incrementAndGet()));
          System.out.println(message);
          new Thread(new Receiver(inputStream, messageQueue, time)).start();
          sema.release();
        }
      } catch (IOException e) {
        e.printStackTrace();
        System.err.println("Unable to start server logic");
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
        System.err.println("Class of a serialized object cannot be found");
      }
    }
  }

  public void connect(int targetId, String host, int port) throws IOException {
    Socket socket = null;
    int retry = 10;
    while (retry > 0) {
      try {
        socket = new Socket(host, port);
        break;
      } catch (IOException e) {
        retry--;
        if (retry == 0)
          throw e;
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e1) {
          e1.printStackTrace();
        }
      }
    }
    ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
    ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
    System.out.println(inputStream);

    Sender sender = new Sender(outputStream);
    Thread senderThread = new Thread(sender);
    senderThread.start();
    senderMap.put(targetId, sender);

    Message message = new Message(nodeId, Message.Type.INI, time.incrementAndGet());
    send(targetId, message);

    new Thread(new Receiver(inputStream, messageQueue, time)).start();
    System.out.println("Connected an exited host");
    sema.release();
  }

  public void send(int id, Message message) {
    senderMap.get(id).send(message);
  }

}
