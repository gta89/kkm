import java.lang.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;


public class kkm {

    static SerialPort com;
    static Socket cs;
    static String IP = "0.0.0.0";
    static int port = 5001;
    static String comN = "COM3";
    static int consoleLog = 2;
    static boolean srv = true;
    static float tc = 0;
    static float ct = 0;



    public static void main(String[] args) {
        if(args.length < 5) {
            System.out.println("Usage: Mode(server/client) IP TcpPort SerialPort Log(0 - off, 1 - log, 2 - StackTrace)");
            return;
        }
        srv = args[0].equals("server");
        IP = args[1];
        port = Integer.parseInt(args[2]);
        comN = args[3];
        consoleLog = Integer.parseInt(args[4]);

        tcpWorker tw = new tcpWorker();
        comWorker cw = new comWorker();
        hbWorker hw = new hbWorker();
        Thread tcpThread = new Thread(tw);
        Thread comThread = new Thread(cw);
        Thread hwThread = new Thread(hw);
        tcpThread.start();
        comThread.start();
        hwThread.start();

    }



    static String byte2hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    static void log(String s) {
        if(consoleLog > 0) {
            System.out.println(new SimpleDateFormat("HH:mm:ss").format(new Date())  +": " +  s);
        }
    }
    static void log(Exception ex) {
        if(consoleLog == 2) {
            ex.printStackTrace();
        }
    }

    private static class hbWorker implements Runnable {
        public void run() {
            String hb = "heartbeat";
            while(true) {
                try {
                    Thread.sleep(900000);
                    cs.getOutputStream().write(hb.getBytes());
                    cs.getOutputStream().flush();
                } catch (IOException | InterruptedException ex) {
                    log(ex);
                }
            }
        }
    }

    private static class tcpWorker implements Runnable {
        public void run() {
            if (srv) {
                ServerSocket ss = null;
                try {
                    ss = new ServerSocket(port, 1, InetAddress.getByName(IP));
                    log("Listening on " + ss.getInetAddress().getHostAddress() + ":" + ss.getLocalPort());
                } catch (IOException ex) {
                    log(ex);
                }
                    while (true) {
                        try {
                            cs = ss.accept();
                            log("Client connected from " + cs.getInetAddress().getHostAddress() + ":" + cs.getPort());
                            if(cs != null) {
                                clientSocket(cs);
                            }
                            log("Client disconnected");
                        } catch (IOException | NullPointerException ex) {
                        log(ex);
                        }
                    }
            } else {
                while (true) {
                    try {
                        log("Trying to connect...");
                        cs = new Socket();
                        cs.connect(new InetSocketAddress(IP, port), 5000);
                        log("Connected to: " + IP + ":" + port);
                        clientSocket(cs);
                    } catch (IOException ex) {
                        log(ex);
                    }
                }
            }
        }

        static void clientSocket(Socket cs) {
            byte[] recv = new byte[2048];
            int bytesCount;
            try {
                cs.setTcpNoDelay(true);
                cs.setKeepAlive(true);
                while (true) {
                    bytesCount = cs.getInputStream().read(recv);
                    if(bytesCount <= 0) continue;
                    byte[] buf = Arrays.copyOf(recv, bytesCount);
                    if(bytesCount == 9 && new String(buf).equals("heartbeat")) {
                        log("received heartbeat");
                        continue;
                    }
                    //tc += bytesCount;
                    log("TCP -> COM: " + byte2hex(buf));
                    //log("Received " + bytesCount + " b, total " + String.format("%.1f", tc/1024) + "kb");
                    com.writeBytes(buf);
                }
            } catch (Exception ex) {
                log(ex);
            }
        }
    }

    private static class comWorker implements Runnable {
        public void run() {
            try {
                com = new SerialPort(comN);
                com.openPort();
                com.setParams(SerialPort.BAUDRATE_9600,
                        SerialPort.DATABITS_8,
                        SerialPort.STOPBITS_1,
                        SerialPort.PARITY_NONE);
                com.addEventListener(new comEventListener(), SerialPort.MASK_RXCHAR);
            } catch (SerialPortException ex) {
                log(ex);
            }
        }


        private static class comEventListener implements SerialPortEventListener {
            public void serialEvent(SerialPortEvent event) {
                int bytesCount = event.getEventValue();
                if (event.isRXCHAR() && bytesCount > 0) {
                    try {
                        byte[] buf = com.readBytes(bytesCount);
                        //ct += bytesCount;
                        log("COM -> TCP: " + byte2hex(buf));
                        //log("Sent " + bytesCount + "b, total " + String.format("%.1f", ct/1024) + "kb");
                        cs.getOutputStream().write(buf);
                        cs.getOutputStream().flush();
                    } catch (SerialPortException | IOException ex) {
                        log(ex);
                    }
                }
            }

        }
    }

}






