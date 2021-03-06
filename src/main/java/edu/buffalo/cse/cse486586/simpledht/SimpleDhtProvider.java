package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;


public class SimpleDhtProvider extends ContentProvider {

    private static final int SERVER_PORT = 10000;

    private String myNodeId = "";

    private String myPort ="";

    private String myPredecessor = null;

    private String mySuccessor = null;

    Uri url;

    static List<String> nodes;

    static final String TAG = SimpleDhtProvider.class.getSimpleName();

    private String largestNodeId = "";

    private String smallestNodeId = "";

    private String predecessorId = "";

    private String successorId = "";

    private MatrixCursor cursor = null;

    String queryReceived = "false";

    private String portQuerying="";

    private String wait = "false";

    private HashMap<String,String> globalProviderMap = new HashMap<String,String>();

    class HashComparator implements Comparator<String>{

        @Override
        public int compare(String node1, String node2) {

            int val1 = Integer.parseInt(node1);
            int val2 = Integer.parseInt(node2);

            String hashNode1 = genHash(String.valueOf(val1/ 2));
            String hashNode2 = genHash(String.valueOf(val2/ 2));

            return hashNode1.compareTo(hashNode2);

        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {

        // TODO Auto-generated method stub
        Context context = this.getContext();

        if(myPredecessor == null && mySuccessor == null){

            if(selection.equals("@") || selection.equals("*")){
                // delete all the files from single.
                Log.d(TAG,"Delete all files from single avd");

                String fileArr[] = context.fileList();

                for(int i=0; i < fileArr.length;i++) {
                    context.deleteFile(fileArr[i]);
                }

            }else{

                Log.d(TAG,"Delete file for key :"+selection);
                context.deleteFile(selection);

            }
        }else{

            // compare the selectionId with my nodeId:
            String selectionId = genHash(selection);

            if(selection.equals("@")) {

                // delete everything from my provider
                Log.d(TAG,"Request for Local Delete all");
                deleteLocalAll();

            }else if(selection.equals("*")){

                // global query
                Log.d(TAG,"Request for Global Delete");
                deleteGlobalAll();


            }else if (selectionId.compareTo(predecessorId) > 0 && selectionId.compareTo(myNodeId) <= 0 && (myNodeId.compareTo(predecessorId) > 0)) {
                // insert data in my nodeId
                Log.d(TAG, "Inside delete case 1, selection belongs to my provider");

                context.deleteFile(selection);

            }else if(myNodeId.equals(smallestNodeId) && selectionId.compareTo(largestNodeId) > 0){

                Log.d(TAG, "Inside delete case 2, selection belongs to my provider");
                Log.d("My Node Id: " ,myNodeId);
                Log.d("largestNodeId",largestNodeId);

                context.deleteFile(selection);

            }else if(myNodeId.equals(smallestNodeId) && (selectionId.compareTo(myNodeId) <= 0)){

                Log.d(TAG, "Inside delete case 3, selection belongs to my provider");
                Log.d("My Node Id: " ,myNodeId);
                Log.d("smallestNodeId",smallestNodeId);

                context.deleteFile(selection);


            }else if(selectionId.compareTo(myNodeId) > 0){

                Log.d(TAG,"delete for key: "+selection+"does not belong to my port,sending to succesor port: " +mySuccessor);

                Log.d("My Node Id: " ,myNodeId);
                Log.d("selectionId",selectionId);

                sendDeleteMsg(selection, mySuccessor);


            }else if(selectionId.compareTo(predecessorId) <= 0){

                Log.d(TAG,"delete for key: "+selection+"does not belong to my port,sending to predecessor port: " +myPredecessor);

                Log.d("My Node Id: " ,myNodeId);
                Log.d("Predecessor Id: ", predecessorId);
                Log.d("selectionId",selectionId);

                sendDeleteMsg(selection,myPredecessor);
            }

        }

        return 0;


    }


    public void deleteLocalAll(){

        Log.d(TAG,"Delete all files from my avd");

        Context context = this.getContext();

        String fileArr[] = context.fileList();

        for(int i=0; i < fileArr.length;i++) {
            context.deleteFile(fileArr[i]);
        }

    }


    public void deleteGlobalAll(){

        Log.d(TAG,"Delete all files from my avd");

        Context context = this.getContext();

        String fileArr[] = context.fileList();

        for(int i=0; i < fileArr.length;i++) {
            context.deleteFile(fileArr[i]);
        }

        // send a global delete message to all other avds:
        /*Message msg = new Message();
        msg.setMessageType("Delete");
        msg.setToPortId(mySuccessor);
        if(!"".equals(portQuerying))
            msg.setQueryingPort(portQuerying);
        else
            msg.setQueryingPort(myPort)lo*/

        // create a global delete msg to send to other avds
        Message msg = createGlobalDeleteMsg();

        // send a delete for global delete of all data to all avds.
        new QueryTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);


    }


    private Message createGlobalDeleteMsg(){

        Message msg = new Message();
        msg.setMessageType("Delete");
        msg.setToPortId(mySuccessor);
        if(!"".equals(portQuerying))
            msg.setQueryingPort(portQuerying);
        else
            msg.setQueryingPort(myPort);

        return msg;

    }


    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        Log.d("insert", values.toString());

        String key = values.get("key").toString();
        String value = values.get("value").toString();

        Log.d(TAG,"Key: "+key);
        Log.d(TAG,"Value: "+value);

        // hash the key
        String keyId = genHash(key);

        // Case 1: insert key/value in local node
        if(myPredecessor == null && mySuccessor == null) {

            Log.d(TAG,"I am the only avd in the ring");

            if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
                writeToFile(key, value);
            }
        }else {

            Log.d("My Node Id: " ,myNodeId);
            Log.d("Predecessor Id: ", predecessorId);
            Log.d("keyId",keyId);

            Log.d(TAG,"Comparison to predecessorId:"+keyId.compareTo(predecessorId));
            Log.d(TAG,"Comparison to myNodeId:"+keyId.compareTo(myNodeId));

            /// check if the key needs to be inserted in this node or successor node:
            if (keyId.compareTo(predecessorId) > 0 && keyId.compareTo(myNodeId) <= 0 && (myNodeId.compareTo(predecessorId) > 0)) {
                // insert data in my nodeId
                Log.d(TAG,"Inside case 1, key belongs to my port");
                Log.d(TAG, "Inserting Key : " + key + " Value : " + value + " in port " + myPort);
                writeToFile(key, value);

            }else if(myNodeId.equals(smallestNodeId) && keyId.compareTo(largestNodeId) > 0){

                Log.d(TAG,"Inside insert case 2, key belongs to my port");
                Log.d(TAG, "Inserting Key : " + key + " Value : " + value + " in port " + myPort);

                writeToFile(key, value);

            }else if(myNodeId.equals(smallestNodeId) && (keyId.compareTo(myNodeId) <= 0)){

                Log.d(TAG,"Inside insert case 3, key belongs to my port");
                Log.d(TAG, "Inserting Key : " + key + " Value : " + value + " in port " + myPort);
                writeToFile(key, value);

            }else if(keyId.compareTo(myNodeId) > 0){
                // send the message to successor

                Log.d(TAG,"Inside insert case 4");
                Log.d(TAG, "Key:" + key + " does not belong to my port, sending to successor port for insert: " + mySuccessor);
                sendInsertMsg(key, value, mySuccessor);

            }else if(keyId.compareTo(predecessorId) <= 0){

                Log.d(TAG,"Inside insert case 5");
                Log.d(TAG, "Key:" + key + " does not belong to my port, sending to predecessor port for insert: " + myPredecessor);

                Log.d("My Node Id: " ,myNodeId);
                Log.d("Predecessor Id: ", predecessorId);
                Log.d("keyId",keyId);

                Log.d("KeyId compare NodeId : ", ""+keyId.compareTo(myNodeId));
                Log.d("KeyId comp PredeceId :", ""+keyId.compareTo(predecessorId));

                sendInsertMsg(key, value, myPredecessor);

            }
        }

        return null;
    }


    private void writeToFile(String key, String value){

        Log.d(TAG, "Writing to File");

        FileOutputStream fos = null;

        try {

            // write data to my content povider.
            fos = getContext().openFileOutput(key, Context.MODE_PRIVATE);
            fos.write(value.getBytes());
            fos.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void sendInsertMsg(String key, String value, String port){

        Message msg = new Message();
        msg.setMessageType("Insert");
        msg.setKeySelection(key);
        msg.setKeyValue(value);
        msg.setToPortId(port);

        Log.d(TAG, "Sending insert message to port: "+port);
        new QueryTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);

    }


    private void sendDeleteMsg(String selection,String port){

        Message msg = new Message();
        msg.setMessageType("Delete");
        msg.setKeySelection(selection);
        msg.setToPortId(port);
        msg.setQueryingPort(myPort);

        Log.d(TAG,"Sending delete message to port: "+port);
        new QueryTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);

    }



    @Override
    public boolean onCreate() {

        // TODO Auto-generated method stub
        // Get my port details:
        /*** set the portNo***/
        TelephonyManager tel = (TelephonyManager) this.getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        /** set the content provider uri ****//*
         * Create a server socket as well as a thread (AsyncTask) that listens on the server
         * port
        **/
        try{

            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        }catch(Exception e){

            e.printStackTrace();
            Log.e(TAG, "Can't create a ServerSocket");

        }

        this.url = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
        // Set nodeId of this content provider

        Log.d(TAG, "Port Str : " + portStr);

        //nodeId = genHash("5554")
        myNodeId = genHash(portStr);

        Log.d(TAG, "my node id" + myNodeId);

        if(!portStr.equals("5554")){

            // send a join request to this node
            // Join-5554
                /*StringBuilder message = new StringBuilder("Join");
                message.append("-");
                message.append(myPort);
                message.append("-");
                message.append(myNodeId);*/
            Message message = new Message();
            message.setMessageType("Join");
            message.setSenderPort(myPort);
            message.setToPortId("11108");

            // send a join request to avd-5544
            //new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message.toString(), "11108");
            new QueryTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message);

        }else{

            nodes = new ArrayList<String>();
            nodes.add(myPort);
            Log.d(TAG,"Port 11108 added to the node list");
        }

        return false;
    }


    private Uri buildUri(String scheme, String authority) {

        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }


    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {

        // TODO Auto-generated method stub

        String[] columns= {"key", "value"};
        cursor = new MatrixCursor(columns);

        try {

            if(myPredecessor == null && mySuccessor == null) {

                if (selection.equals("*") || selection.equals("@")) {


                    Log.d(TAG, "Quering for all in my Local AVD :" + selection);
                    // get all the files from single AVD
                    queryLocalAll();

                }else {

                    Log.d(TAG, "Query for single selection in my Local AVD: " + selection);
                    return queryMyProvider(selection);
                }
            }else{

                // compare the selectionId with my nodeId:
                String selectionId = genHash(selection);

                if(selection.equals("@")) {
                    Log.d(TAG,"Local all query received");
                    queryLocalAll();

                }else if(selection.equals("*")){

                    Log.d(TAG,"Global query received");
                    // global query
                    queryGlobalAll();


                }else if (selectionId.compareTo(predecessorId) > 0 && selectionId.compareTo(myNodeId) <= 0 && (myNodeId.compareTo(predecessorId) > 0)) {
                    // insert data in my nodeId
                    Log.d(TAG, "Inside query case 1, key belongs to my port");

                    return queryMyProvider(selection);

                }else if(myNodeId.equals(smallestNodeId) && selectionId.compareTo(largestNodeId) > 0){

                    Log.d(TAG, "Inside query case 2, key belongs to my port");
                    Log.d("My Node Id: " ,myNodeId);
                    Log.d("smallestNodeId: ", smallestNodeId);

                    return queryMyProvider(selection);

                }else if(myNodeId.equals(smallestNodeId) && (selectionId.compareTo(myNodeId) <= 0)){

                    Log.d(TAG, "Inside query case 3, key belongs to my port");
                    Log.d("My Node Id: " ,myNodeId);
                    Log.d("smallestNodeId: ", smallestNodeId);

                    return queryMyProvider(selection);

                }else if(selectionId.compareTo(myNodeId) > 0){

                    Log.d(TAG,"query for key: "+selection+" does not belong to my port,sending to succesor port: " +mySuccessor);
                    Log.d("My Node Id: " ,myNodeId);
                    Log.d("selection Id: ", selectionId);
                    sendQueryMsg(selection,mySuccessor);


                }else if(selectionId.compareTo(predecessorId) <= 0){

                    Log.d(TAG,"query for key: "+selection+" does not belong to my port,sending to predecessor port: " +myPredecessor);
                    Log.d("My Node Id: " ,myNodeId);
                    Log.d("Predecessor Id: ", predecessorId);
                  //  Log.d("keyId",keyId);

                   // Log.d("KeyId compare NodeId : ", ""+keyId.compareTo(myNodeId));
                    //Log.d("KeyId comp PredeceId :", ""+keyId.compareTo(predecessorId));
                    sendQueryMsg(selection,myPredecessor);
                }

            }

        }catch(Exception e){
            e.printStackTrace();
            Log.d(TAG, "Retrieving value from file failed");
        }
        return cursor;
    }


    private void queryGlobalAll(){

        String[] columns= {"key", "value"};
        //MatrixCursor cursor = new MatrixCursor(columns);

        String[] fileArr = getContext().fileList();

        Log.d(TAG, "------File list retrieved for this AVD----- :" + fileArr);

        FileInputStream inputStream;
        BufferedReader bufferedReader;
        HashMap<String,String> map = new HashMap<String, String>();

        try {

            for (int i = 0; i < fileArr.length; i++) {

                StringBuilder msg = new StringBuilder();

                inputStream = getContext().openFileInput(fileArr[i]);

                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line;

                while ((line = bufferedReader.readLine()) != null) {
                    msg.append(line);
                }

                bufferedReader.close();
                inputStream.close();

                Log.d(TAG, " Value :" + msg + " retrieved for key :" + fileArr[i]);

                cursor.addRow(new String[]{fileArr[i], msg.toString()});
                map.put(fileArr[i], msg.toString());

            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // send globalQuery message to all AVD's
       /* Message message = new Message();
        message.setMessageType("QueryGlobal");
        message.setQueryingPort(myPort);
        message.setKeyValueMap(map);
        message.setToPortId(mySuccessor);

*/

        // create global query message
        Message message = createGlobalQuery(map);

        new QueryTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message);

        // busywait until data from all other AVD's is received
        busyWait();

    }


    private Message createGlobalQuery(HashMap<String,String> map){

        Message message = new Message();
        message.setMessageType("QueryGlobal");
        message.setQueryingPort(myPort);
        message.setKeyValueMap(map);
        message.setToPortId(mySuccessor);

        return message;
    }

    private void busyWait(){

        // busy wait till a reply is received from the receiver

        // wait till the wait is set to true on server side.
        while(true){
            if("true".equals(wait))
                break;
        }

        // reset wait to false
        wait = "false";

    }


    private void sendQueryMsg(String selection, String successorPort){

        Message message = new Message();
        message.setToPortId(successorPort);
        if("true".equals(queryReceived))
            message.setQueryingPort(portQuerying);
        else
            message.setQueryingPort(myPort);

        message.setMessageType("Query");
        message.setKeySelection(selection);

        new QueryTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message);

        if("false".equals(queryReceived)){
            busyWait();
        }

        resetQueryFields();
    }



    private Cursor queryMyProvider(String selection){

        String[] columns= {"key", "value"};
        MatrixCursor cursor = new MatrixCursor(columns);

        FileInputStream inputStream;
        BufferedReader bufferedReader;

        HashMap<String,String> dataMap = new HashMap<String,String>();


        try {

            inputStream = getContext().openFileInput(selection);

            bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            /*** read the message value from the file ***/
            String line;
            StringBuilder value = new StringBuilder();

            while ((line = bufferedReader.readLine()) != null) {
                value.append(line);
            }

            Log.d(TAG, "Value: " + value + " retrieved for key : " + selection);

            bufferedReader.close();
            inputStream.close();

            cursor.addRow(new String[]{selection, value.toString()});
            dataMap.put(selection,value.toString());

        }catch (FileNotFoundException e) {
            e.printStackTrace();
        }catch (IOException e) {
            e.printStackTrace();
        }

        Log.d("Query Flag :", "" + queryReceived);

        if("true".equals(queryReceived)){

            /*Message queryReply = new Message();
            queryReply.setMessageType("QueryReply");
            queryReply.setKeyValueMap(dataMap);
            queryReply.setToPortId(portQuerying);
            queryReply.setSenderPort(myPort);*/

            Message queryReply = createQueryReplyMsg(dataMap);

            new QueryTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryReply);

        }

        resetQueryFields();

        return cursor;

    }


    private Message createQueryReplyMsg(HashMap<String,String> dataMap){

        Message queryReply = new Message();
        queryReply.setMessageType("QueryReply");
        queryReply.setKeyValueMap(dataMap);
        queryReply.setToPortId(portQuerying);
        queryReply.setSenderPort(myPort);

        return queryReply;

    }

    private void queryLocalAll() {

        String[] columns= {"key", "value"};
        //MatrixCursor cursor = new MatrixCursor(columns);

        String[] fileArr = getContext().fileList();

        Log.d(TAG, "------File list retrieved for this AVD----- :" + fileArr);

        FileInputStream inputStream;
        BufferedReader bufferedReader;
        try {

            for (int i = 0; i < fileArr.length; i++) {

                StringBuilder msg = new StringBuilder();

                inputStream = getContext().openFileInput(fileArr[i]);

                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line;


                while ((line = bufferedReader.readLine()) != null) {
                    msg.append(line);
                }

                bufferedReader.close();
                inputStream.close();

                Log.d(TAG, " Value :" + msg + " retrieved for key :" + fileArr[i]);

                cursor.addRow(new String[]{fileArr[i], msg.toString()});
                globalProviderMap.put(fileArr[i],msg.toString());

            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // if this was a global query received from another avd, send it to your successor

        if("true".equals(queryReceived)){

            // send the data back to the sender.
            /*Message msg = new Message();
            msg.setKeyValueMap(globalProviderMap);
            msg.setToPortId(mySuccessor);
            msg.setQueryingPort(portQuerying);
            msg.setMessageType("QueryGlobal");*/

            Message msg = createGlobalQueryReply();

            new QueryTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);

            resetQueryFields();

        }

    }

    private Message createGlobalQueryReply(){

        // sending my entire provider data back to the sender for globalQuery
        Message msg = new Message();
        msg.setKeyValueMap(globalProviderMap);
        msg.setToPortId(mySuccessor);
        msg.setQueryingPort(portQuerying);
        msg.setMessageType("QueryGlobal");

        return msg;
    }


    private void resetQueryFields(){
        Log.d(TAG,"Reset query fields");
        queryReceived = "false";
        // reset Port Query
        portQuerying = "";
    }


    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }


    private String genHash(String input){

        MessageDigest sha1 = null;
        try {

            sha1 = MessageDigest.getInstance("SHA-1");

        } catch (NoSuchAlgorithmException e) {

            e.printStackTrace();

        }
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }


    private class QueryTask extends AsyncTask<Message, Void, Void> {

        @Override
        protected Void doInBackground(Message... msgs) {

            // String queryReply = "";
            Message queryReply = null;
            try {

                Message msgToSend = msgs[0];
                //Log.d(TAG, " Query Message To Send : " + msgToSend);
                //PrintWriter out;
                Socket socket;

                String remotePort = msgToSend.getToPortId();

                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(remotePort));

                //out = new PrintWriter(socket.getOutputStream(), true);
                ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));


                Log.d(TAG, " Sending " + msgs[0] + "message to port: " + remotePort);

                if (msgToSend != null)
                    out.writeObject(msgToSend);

                out.flush();
                out.close();
                socket.close();

                Log.d(TAG, "Message sent to port:" + remotePort);

            } catch (UnknownHostException e) {

                Log.e(TAG, "ClientThread UnknownHostException");

            } catch (IOException e) {

                e.printStackTrace();

            }

            return null;
        }
    }


    public class ServerTask extends AsyncTask<ServerSocket, String, Void>  {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {

            ServerSocket serverSocket = sockets[0];
            Socket socket = null;

            try {

                while(true) {

                    socket = serverSocket.accept();
                    InputStreamReader inputStreamReader = new InputStreamReader(socket.getInputStream());
                    BufferedReader br = new BufferedReader(inputStreamReader);

                    ObjectInputStream input = new ObjectInputStream(new BufferedInputStream(
                            socket.getInputStream()));

                    // String msg = br.readLine();

                    //String [] msgArr = msg.split("-");

                    Message message = (Message)input.readObject();

                    Log.d(TAG,"Message of type: "+message.getMessageType());

                    if("Join".equalsIgnoreCase(message.getMessageType())){

                        // message of type join received from other avd

                        handleJoinRequest(message);

                    }else if("JoinReply".equalsIgnoreCase(message.getMessageType())){


                        // update my predecessor and successor nodes

                        // Message Format : JoinReply-PortId-Successor-Predecessor
                        myPredecessor = String.valueOf(Integer.parseInt(message.getPredecessorPort())*2);
                        mySuccessor = String.valueOf(Integer.parseInt(message.getSuccessorPort()) * 2);

                        Log.d("Predecessor received: ", myPredecessor);
                        Log.d("Successor received:",mySuccessor);

                        predecessorId = genHash(message.getPredecessorPort());
                        successorId = genHash(message.getSuccessorPort());

                        // updated largestId and smallestId
                        smallestNodeId = message.getSmallestId();
                        largestNodeId = message.getLargestId();

                        Log.d(TAG,"Updated Predecessor Node to: "+myPredecessor +"for portId : "+myPort);
                        Log.d(TAG,"Updated Successor Node to: "+mySuccessor + "for portId: "+myPort);

                        Log.d(TAG,"Updated Predecessor Id to: " + predecessorId +"for port: "+myPort);
                        Log.d(TAG,"Updated Successor Id to: " + successorId +"for port: "+myPort);


                        Log.d(TAG,"Updated Largest Id to:"+largestNodeId);
                        Log.d(TAG,"Updated Smallest Id to:"+smallestNodeId);

                    }else if("Delete".equalsIgnoreCase(message.getMessageType())){
                        delete(url, message.getKeySelection(), null);
                    }else if("DeleteGlobal".equalsIgnoreCase(message.getMessageType())){

                        if(!myPort.equals(message.getQueryingPort())){

                            portQuerying = message.getQueryingPort();
                            delete(url,"@",null);

                        }

                    }else{
                        handleMessage(message);
                    }
                }

            } catch(ClassNotFoundException e) {
                e.printStackTrace();
            }catch (IOException e){
                Log.v(TAG,e.getMessage());
                e.printStackTrace();
            }finally{
                if(socket != null) {
                    try {
                        socket.close();
                    }catch(IOException e){
                        Log.v(TAG,e.getMessage());
                        e.printStackTrace();
                    }
                }
            }

            return null;
        }


        public void handleJoinRequest(Message message){

            // add it to the nodeList
            Log.d(TAG,"Node to add:"+message.getSenderPort());
            nodes.add(message.getSenderPort());

            Collections.sort(nodes,new HashComparator());

            Log.d(TAG, "Nodes after sort:" + nodes);

            //Generate new messages and send it to client task with successor and predecessor ports
            int n = nodes.size()-1;

            largestNodeId = genHash(String.valueOf(Integer.parseInt(nodes.get(n)) / 2));
            smallestNodeId = genHash(String.valueOf(Integer.parseInt(nodes.get(0)) / 2));

            Log.d(TAG,"Updated largest Id to :"+largestNodeId);
            Log.d(TAG,"Updated smallest Id to:"+smallestNodeId);


            Log.d(TAG,"Total number of nodes :" + nodes.size());

            for(int i = 0; i < nodes.size(); i++){

                String successor = null;
                String predecessor = null;

                String portId = nodes.get(i);

                if(i == 0){

                    // first node
                    successor = nodes.get(i+1);
                    predecessor = nodes.get(n);


                }else if(i == n){

                    // last node
                    successor = nodes.get(0);
                    predecessor = nodes.get(i-1);

                }else{

                    successor = nodes.get(i+1);
                    predecessor = nodes.get(i-1);
                }

                Log.d(TAG, "Port Id : " + nodes.get(i) + "Successor :" + successor + "Predecessor:" + predecessor);


                // send reply for join to sending port
                Message msgReply = new Message();
                msgReply.setMessageType("JoinReply");
                msgReply.setSuccessorPort(String.valueOf(Integer.parseInt(successor) / 2));
                msgReply.setPredecessorPort(String.valueOf(Integer.parseInt(predecessor) / 2));
                msgReply.setLargestId(largestNodeId);
                msgReply.setSmallestId(smallestNodeId);
                msgReply.setToPortId(portId);
                // msgToSend : JoinReply-PortId-Successor-Predecessor-smallestId-largestId

                if(!portId.equals(myPort)) {
                    //new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend.toString(), portId);
                    new QueryTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgReply);
                }else{

                    // setting avd-5554
                    myPredecessor = predecessor;
                    mySuccessor = successor;

                    // update my predecessor and successor
                    predecessorId = genHash(String.valueOf(Integer.parseInt(myPredecessor)/ 2));
                    successorId = genHash(String.valueOf(Integer.parseInt(mySuccessor)/ 2));

                }

            }
        }


        private void handleMessage(Message msg){

            String messageType = msg.getMessageType();
            Log.d(TAG, "Message of type :" + messageType + " received");

            if("Insert".equals(messageType)){

                /// check if the message belongs to my Node:
                // call insert
                ContentValues values = new ContentValues();
                values.put("key",msg.getKeySelection());
                values.put("value",msg.getKeyValue());
                Log.d(TAG,"Url :" + url);
                // call insert
                insert(url, values);

            }else if("Query".equalsIgnoreCase(messageType)){

                queryReceived = "true";
                portQuerying = msg.getQueryingPort();

                String selection  = msg.getKeySelection();
                // query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                // String sortOrder)
                query(url,null,selection,null,null);

                // loop through the cursor and create a Message object:
            }else if("QueryReply".equalsIgnoreCase(messageType)){

                String[] columns= {"key", "value"};
                cursor = new MatrixCursor(columns);
                // retrieve the values from hasmap and set it in the cursor
                HashMap<String,String> map = msg.getKeyValueMap();
                for(Map.Entry<String,String> entry : map.entrySet()){
                    String[] row = {entry.getKey(), entry.getValue()};
                    // add to my cursor
                    cursor.addRow(row);
                }

                // set the waitFlag to true;
                wait = "true";

            }else if("QueryGlobal".equalsIgnoreCase(messageType)){

                // check if the global query was sent by me only
                // queryPort = msg.getQueryPort();
                Log.d(TAG,"Query Port: " +portQuerying);

                if(msg.getQueryingPort().equals(myPort)){

                    // get the data received from other avd in a map
                    HashMap<String,String> map = msg.getKeyValueMap();
                    Log.d(TAG,"Map: "+msg.getKeyValueMap());

                    String[] columns= {"key", "value"};
                    cursor = new MatrixCursor(columns);
                    // retrieve data from global map and save it in my cursor
                    for(Map.Entry<String,String> entry : map.entrySet()){
                        String[] row = {entry.getKey(), entry.getValue()};
                        cursor.addRow(row);
                    }
                    // set the waitFlag to true;
                    wait = "true";

                }else{
                    // port who sent the query
                    portQuerying = msg.getQueryingPort();
                    // set the queryReceived flag
                    queryReceived = "true";
                    // get the previous data
                    globalProviderMap = msg.getKeyValueMap();
                    // query my provider
                    query(url,null,"@",null,null);

                }
            }
        }
    }
}
