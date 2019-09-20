package co.kr.fvn.wp;

import java.io.*;
import java.util.*;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import android.util.*;

/**
 * @author LBO
 */
public class BluetoothService {

    // Debugging
    private static final String TAG = "BluetoothService";
    private static final boolean D = false;
    
    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
        UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID MY_UUID_INSECURE =
        UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    // RFCOMM Protocol
 	private static final UUID MY_UUID = 
 		UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    //00001101-0000-1000-8000-00805f9b34fb
    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private Context mContext;
    
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    
	public BluetoothService( Context context, Handler handler ){
		// TODO Auto-generated constructor stub
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mContext = context;
        mHandler = handler;
	}
	
    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState( int state ){
        if( D ) Log.d( TAG, "setState() " + mState + " -> " + state );
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage( MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }
    
    /**
     * Return the current connection state. */
    public synchronized int getState() 
    {
        return mState;
    }
    
    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if( D ){
            Log.d( TAG, "start" );
        }
        // Cancel any thread attempting to make a connection
        if( mConnectThread != null ) {
        	mConnectThread.cancel(); 
        	mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if( mConnectedThread != null ) {
        	mConnectedThread.cancel(); 
        	mConnectedThread = null;
        }

        setState( STATE_LISTEN );
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect( BluetoothDevice device ) {
        if( D ){
            Log.d( TAG, "connect to: " + device );
        }

        // Cancel any thread attempting to make a connection
        if( mState == STATE_CONNECTING ) {
            if( mConnectThread != null ) {
            	mConnectThread.cancel(); 
            	mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if( mConnectedThread != null ) {
        	mConnectedThread.cancel(); 
        	mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread( device );
        mConnectThread.start();
        setState( STATE_CONNECTING );
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected( BluetoothSocket socket, BluetoothDevice device ) {
        if( D ){
            Log.d( TAG, "connected:" );
        }

        // Cancel the thread that completed the connection
        if( mConnectThread != null ) {
        	mConnectThread.cancel(); 
        	mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if( mConnectedThread != null ) {
        	mConnectedThread.cancel(); 
        	mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread( socket );
        mConnectedThread.start();
        
        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage( MainActivity.MESSAGE_DEVICE_NAME );
        Bundle bundle = new Bundle();
        bundle.putString( MainActivity.DEVICE_NAME, device.getName() );
        msg.setData( bundle );
        mHandler.sendMessage( msg );        

        setState( STATE_CONNECTED );
    }

    /**
     * Stop all threads
     */
    public synchronized void stop(){
        if( D ){
            Log.d( TAG, "stop" );
        }

        if( mConnectThread != null ){
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if( mConnectedThread != null ){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState( STATE_NONE );
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write( byte[] out ){
        // Create temporary object
        ConnectedThread r;
        
        // Synchronize a copy of the ConnectedThread
        synchronized( this )
        {
            if( mState != STATE_CONNECTED ) {
                Log.d(TAG, "=======11=====: " + mState);
                return;
            }
            r = mConnectedThread;
        }
        Log.d(TAG, "============: " + mState);
        // Perform the write unsynchronized
        r.write( out );
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
    	// Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage( MainActivity.MESSAGE_TOAST );
        Bundle bundle = new Bundle();
        bundle.putString( MainActivity.TOAST, "Unable to connect device" );
        msg.setData( bundle );
        mHandler.sendMessage( msg );
        
        // Start the service over to restart listening mode
        BluetoothService.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage( MainActivity.MESSAGE_TOAST );
        Bundle bundle = new Bundle();
        bundle.putString( MainActivity.TOAST, "Device connection was lost" );
        msg.setData( bundle );
        mHandler.sendMessage( msg );
        
        // Start the service over to restart listening mode
        BluetoothService.this.start();
    }
    
    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread( BluetoothDevice device ) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
            	tmp = device.createRfcommSocketToServiceRecord( MY_UUID );
            } catch( IOException e ){
                Log.e( TAG, "create() failed", e );
            }
            
            mmSocket = tmp;
        }

        public void run() {
            //Log.i( TAG, "BEGIN mConnectThread" );
            setName( "ConnectThread" );

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } 
            catch( IOException e ) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch( IOException e2 ) {
                    Log.e( TAG, "unable to close() socket during connection failure", e2 );
                }
                
                connectionFailed();
                
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized( BluetoothService.this )
            {
                mConnectThread = null;
            }

            // Start the connected thread
            connected( mmSocket, mmDevice );
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch( IOException e ) {
                Log.e( TAG, "close() of connect socket failed", e );
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        Boolean stopWorker = false;
        int readBufferPosition = 0;
        byte[] readBuffer = new byte[1024];

        public ConnectedThread( BluetoothSocket socket ){
            //Log.d( TAG, "create ConnectedThread" );
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch( IOException e ){
                Log.e( TAG, "temp sockets not created", e );
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            //Log.i( TAG, "BEGIN mConnectedThread" );
            
            byte[] buffer = new byte[1024];
            int bytes;

            int availableBytes = 0;

            // Keep listening to the InputStream while connected
            while( true ) {
                try {
                	byte[] tmpBuf = null;

                    // Read from the InputStream
                    bytes = mmInStream.read( buffer );
                    //Log.d( TAG, "Instream Data - Length : " + buffer.length + ", data : " + HexUtils.hexToString( buffer, 0, bytes) );
                                        
                    if( bytes > 0 ) {
                    	//Log.d( TAG, "read data size : " + bytes );
                    	tmpBuf = new byte[bytes];
                    	System.arraycopy( buffer, 0, tmpBuf, 0, bytes );
                        for(int i=0;i<tmpBuf.length;i++) {
                            byte b = tmpBuf[i];
                            if(b == '\n') {
                                byte[] encodedBytes = new byte[readBufferPosition];
                                System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                final String data = new String(encodedBytes, "US-ASCII");
                                //Log.d("bytesAvailable=end===", ""+data);

                                readBufferPosition = 0;
                                readBuffer = new byte[1024];
                                mHandler.obtainMessage( MainActivity.MESSAGE_READ, bytes, -1, data ).sendToTarget();
                            } else {
                                readBuffer[readBufferPosition++] = b;
                            }
                        }
                    }
                    // 이 부분에서 Main Activity와 연결 시도하여 그래픽을 그린다.
                    // Send the obtained bytes to the UI Activity
                    //mHandler.obtainMessage( MainActivity.MESSAGE_READ, bytes, -1, tempRespon ).sendToTarget();
                    //tmpBuf = null;
                } catch( IOException e ) {
                    Log.e( TAG, "disconnected", e );
                    
                    connectionLost();
                    // Start the service over to restart listening mode
                    BluetoothService.this.start();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write( byte[] buffer ){
            try {
                mmOutStream.write( buffer );

                // Share the sent message back to the UI Activity
               // mHandler.obtainMessage( BluetoothChat.MESSAGE_WRITE, -1, -1, buffer ).sendToTarget();
            } catch( IOException e ){
                Log.e( TAG, "Exception during write", e );
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch( IOException e ) {
                Log.e( TAG, "close() of connect socket failed", e );
            }
        }
    } 
}
