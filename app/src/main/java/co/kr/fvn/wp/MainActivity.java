package co.kr.fvn.wp;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private Button btn_reset,btn_edit,btn_insert,btn_start,btn_stop,btn_continue,btn_washer,btn_drain,btn_vent,btn_ble_conn;
    private EditText et_set_top_cnt,et_set_pump_cnt,et_set_volt,et_set_volt_dec,et_set_am,et_set_am_dec,et_set_on,et_set_off,et_set_delay,et_set_flooding;
    private TextView tv_time,tv_total_cnt,tv_top_state,tv_top_volt,tv_top_am,tv_wm,tv_fm,tv_dm,btn_conn;
    private String sepa = ":";
    private String readData = "";
    private String finalData = "";

    private ProgressDialog asyncDialog;
    private byte[] tempCom;
    private String tempSpiVal="";
    // Debugging
    private static final String TAG = "MainActivity";
    private String mailTxt = "";
    private int mailCnt = 0;
    private static final boolean D = false;
    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_LE_READ = 201;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 100;
    // Name of the connected device
    private String mConnectedDeviceName = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothService mBleClasicService = null;
    private BleManager mBleLeManager = null;
    private ServiceHandler mServiceHandler = new ServiceHandler();

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage( Message msg )
        {
            //super.handleMessage( msg );
            //Log.i( TAG, "Handler_msg: " + msg.what );
            //Log.i( TAG, "Handler_msg: " + msg.arg1 );
            switch( msg.what )
            {
                case MESSAGE_STATE_CHANGE:
                    if( D ) Log.i( TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1 );
                    switch( msg.arg1 )
                    {
                        case BluetoothService.STATE_CONNECTED:
                            setStatus( getString(R.string.title_connected_to, mConnectedDeviceName) );
                            //mConversationArrayAdapter.clear();
                            break;

                        case BluetoothService.STATE_CONNECTING:
                            setStatus( R.string.title_connecting );
                            break;

                        case BluetoothService.STATE_LISTEN:
                            break;
                        case BluetoothService.STATE_NONE:
                            setStatus( R.string.title_not_connected );
                            break;
                        case BleManager.STATE_CONNECTED:
                            setStatus( getString(R.string.title_connected_to, mConnectedDeviceName) );
                            break;
                    }
                    break;
                case BleManager.STATE_CONNECTED:
                    setStatus( getString(R.string.title_connected_to, mConnectedDeviceName) );
                    asyncDialog.dismiss();
                    Toast.makeText(getApplicationContext(), "Connected to "+mConnectedDeviceName,Toast.LENGTH_SHORT).show();
                    btn_ble_conn.setText("BLE 통신중");
                    btn_ble_conn.setEnabled(false);
                    TimerTask timerTaskSend = new TimerTask(){
                        @Override
                        public void run(){
                            writeToBle("$RSETVAL");
                        }
                    };
                    Timer timerSend = new Timer(true);
                    timerSend.schedule(timerTaskSend,1000);
                    break;
                case MESSAGE_READ:
                    /*byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String( readBuf, 0, msg.arg1 );
                    Log.d( TAG, "readMessage : " + readMessage + ", " + HexUtils.hexToString( readBuf, 0, readBuf.length) + ", length : " + readBuf.length );*/
                    if(msg.obj != null) {
                        String readMessage = (String) msg.obj;
                        //Log.d( TAG, "readMessage : " + readMessage);
                        //addValue( readMessage ); 데이터 셋팅팅
                        try{
                            setData(readMessage);
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                   }
                    break;

                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);

                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;

                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_LE_READ:
                    if(msg.obj != null) {
                        String readMessageLe = (String) msg.obj;
                        //Log.d( TAG, "readMessage : " + readMessageLe);
                        //Log.d( TAG, "readMessage : " + readMessageLe);
                        //addValue( readMessageLe );
                        //Log.d( TAG, "readMessageLe : " + readMessageLe);
                        try{
                            setData(readMessageLe);
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                    }
                    break;
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUi();
        btnOnClickEvent();
        setBle();
    }
    private boolean writeToBle(String command) {

        if(mBleLeManager == null){
            Toast.makeText(getApplicationContext(), "BLE 연결을 먼저해주세요.",
                    Toast.LENGTH_SHORT).show();
            btn_ble_conn.setText("BLE 찾기");
            btn_ble_conn.setEnabled(true);
            return false;
        }
        Log.d("=======",""+mBleLeManager.getState());
        if(mBleLeManager.getState() != 16){
            Toast.makeText(getApplicationContext(), "BLE 연결을 먼저해주세요.",
                    Toast.LENGTH_SHORT).show();
            btn_ble_conn.setText("BLE 찾기");
            btn_ble_conn.setEnabled(true);
            return false;
        }
        command = command+"@"+"\r"; //블루투스 20바이트만 전송되서 스타트 앤드 붙임
        Log.d("====SEND MSG===>",""+command);
        //mBleClasicService.write(command.getBytes());
        tempCom = command.getBytes();
        if(tempCom.length > 19){
            tempSpiVal = "";
            for(int i =0;i < 20;i++){
                tempSpiVal = tempSpiVal+(char)tempCom[i];
            }
            mBleLeManager.write(null,tempSpiVal.getBytes());

            TimerTask timerTask = new TimerTask(){
                @Override
                public void run(){
                    tempSpiVal = "";
                    for(int i =20;i < tempCom.length;i++){
                        tempSpiVal = tempSpiVal+(char)tempCom[i];
                    }
                    mBleLeManager.write(null,tempSpiVal.getBytes());
                }
            };
            Timer timer = new Timer(true);
            timer.schedule(timerTask,50);
        }else{
            mBleLeManager.write(null,command.getBytes());
        }
        //mSerial.write(command.getBytes(), command.length());
        return true;
    }
    public void setData(String dataVal) {
        //$I10:13.1:5.1:PASS:ING:DM@     $S111:222:11:333:444:55:66.6:77.7@
        //Log.d("=====",""+dataVal);    //finalData 특정 플레그가 오면 던져준다
        readData = readData+dataVal;
        //Log.d("=====",""+readData);
        if(readData.indexOf("$") > -1 && readData.indexOf("@") > -1){
            readData = readData.substring(readData.indexOf("$"),readData.indexOf("@"));
            readData = readData.replaceAll("@","");
            finalData = readData;
            Log.d("====READ MSG===>",""+readData);
            String[] allVal = readData.split(":");
            if(allVal.length == 6){
                tv_total_cnt.setText(allVal[0].substring(2));
                tv_top_volt.setText(allVal[1]);
                tv_top_am.setText(allVal[2]);
                tv_top_state.setText(allVal[3]);
                if(allVal[4].equals("ING")){
                    btn_conn.setText("진행중");
                }
                if(allVal[4].equals("STP")){
                    btn_conn.setText("중지");
                }
                if(allVal[4].equals("FIN")){
                    btn_conn.setText("완료");
                }
                tv_wm.setBackgroundResource(R.color.colorGra);
                tv_wm.setTextColor(Color.parseColor("#000000"));
                tv_fm.setBackgroundResource(R.color.colorGra);
                tv_fm.setTextColor(Color.parseColor("#000000"));
                tv_dm.setBackgroundResource(R.color.colorGra);
                tv_dm.setTextColor(Color.parseColor("#000000"));
                if(allVal[5].equals("WM")){ //진행중인곳
                    tv_wm.setBackgroundResource(R.color.colorGreen);
                    tv_wm.setTextColor(Color.parseColor("#D81B60"));
                }
                if(allVal[5].equals("FM")){ //진행중인곳
                    tv_fm.setBackgroundResource(R.color.colorGreen);
                    tv_fm.setTextColor(Color.parseColor("#D81B60"));
                }
                if(allVal[5].equals("DM")){ //진행중인곳
                    tv_dm.setBackgroundResource(R.color.colorGreen);
                    tv_dm.setTextColor(Color.parseColor("#D81B60"));
                }
            }else if(allVal.length == 8){   //기본 셋팅값

                et_set_top_cnt.setText(allVal[0].substring(2));
                et_set_pump_cnt.setText(allVal[1]);
                et_set_on.setText(allVal[2]);
                et_set_off.setText(allVal[3]);
                et_set_delay.setText(allVal[4]);
                et_set_flooding.setText(allVal[5]);
                String[] voltStr = allVal[6].split("\\.");
                if(voltStr.length > 1){
                    et_set_volt.setText(voltStr[0]);
                    et_set_volt_dec.setText(voltStr[1]);
                }
                String[] amStr = allVal[7].split("\\.");

                if(amStr.length > 1){
                    et_set_am.setText(amStr[0]);
                    et_set_am_dec.setText(amStr[1]);
                }

                btn_edit.setEnabled(true);
                btn_insert.setEnabled(false);
                et_set_top_cnt.setEnabled(false);
                et_set_pump_cnt.setEnabled(false);
                et_set_volt.setEnabled(false);
                et_set_am.setEnabled(false);
                et_set_volt_dec.setEnabled(false);
                et_set_am_dec.setEnabled(false);
                et_set_on.setEnabled(false);
                et_set_off.setEnabled(false);
                et_set_delay.setEnabled(false);
                et_set_flooding.setEnabled(false);
            }else{
                Toast.makeText(this, "모듈 데이터 오류~!", Toast.LENGTH_SHORT).show();
            }
            readData = "";
        }
    }
    private void setBle() {
        // BLE 관련 Permission 주기
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            // Android M Permission check
            if(this.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("이 앱은 BLE 위치 정보 액세스 권한이 있어야합니다.");
                builder.setMessage("이 앱이 BLE를 감지 할 수 있도록 위치 정보 액세스 권한을 부여하십시오.");
                builder.setPositiveButton("Ok", null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @TargetApi(Build.VERSION_CODES.M)
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if( mBluetoothAdapter == null ){
            Toast.makeText( this, "Bluetooth is not available", Toast.LENGTH_LONG ).show();
            finish();
            return;
        }
    }
    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();

        if( D ) Log.e( TAG, "++ ON START ++" );

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if( !mBluetoothAdapter.isEnabled() ){
            Intent enableIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
            startActivityForResult( enableIntent, REQUEST_ENABLE_BT );
        }else{
            if( mBleClasicService == null || mBleLeManager == null){
                mBleClasicService = new BluetoothService(this, mHandler);
            }
        }
    }
    @Override
    public void onRequestPermissionsResult(int reqeustCode, String permission[], int[] grantResults) {
        switch (reqeustCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("permission", "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("기능제한 알림");
                    builder.setMessage("BLE위치 정보 액세스 권한이 부여되지 않았으므로, " +
                            "이 앱은 백그라운드에서 BLE 기기를 발견 할 수 없습니다.");
                    builder.setPositiveButton("Ok", null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {

                        }
                    });
                    builder.show();
                }
                return;
            }
        }
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();

        if( D ) Log.e( TAG, "+ ON RESUME +" );

        if( mBleClasicService != null ){
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if( mBleClasicService.getState() == BluetoothService.STATE_NONE ){
                mBleClasicService.start();
            }
        }
    }
    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();

        if( mBleClasicService != null ){
            mBleClasicService.stop();
        }
        if( mBleLeManager != null ){
            mBleLeManager.close();
            mBleLeManager.disconnect();
        }
        System.exit(0); //handeler 러가 죽지 않아서 어플재시작시 화면업데이트가 안됨
        if( D ) Log.e( TAG, "--- ON DESTROY ---" );
    }
    private void ensureDiscoverable()
    {
        if( D ) Log.d( TAG, "ensure discoverable" );

        if( mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE )
        {
            Intent discoverableIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE );
            discoverableIntent.putExtra( BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300 );
            startActivity( discoverableIntent );
        }
    }
    private void connectDevice( Intent data ){   //일반 블루투스 연결
        // Get the device MAC address
        String address = data.getExtras().getString( DeviceListActivity.EXTRA_DEVICE_ADDRESS );
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice( address );
        Log.d( TAG, "1111111111111 " + address );
        Log.d( TAG, "111111111111111 " + device );
        mBleClasicService.connect( device );
    }

    private void connectLeDevice( Intent data  ) //ble연결
    {
        mBleLeManager = BleManager.getInstance(getApplicationContext(), mServiceHandler);
        String address = data.getExtras().getString( DeviceListActivity.EXTRA_DEVICE_ADDRESS );
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice( address );
        mConnectedDeviceName = device.getName();
        mBleLeManager.connectGatt(getApplicationContext(), true, device);
        Log.d( TAG, "2222222222 " + device );
    }

    public void onActivityResult( int requestCode, int resultCode, Intent data ) {
        super.onActivityResult(requestCode, resultCode, data);
        if (D) Log.d(TAG, "onActivityResult " + requestCode);
        asyncDialog = new ProgressDialog(MainActivity.this);
        asyncDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        asyncDialog.setMessage("BLE 연결중 입니다...");
        asyncDialog.show();
        Log.d(TAG, "onActivityResult=== " + requestCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:    //연결성공시
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

                    int deviceType = device.getType();
                    if (deviceType == BluetoothDevice.DEVICE_TYPE_CLASSIC) {    //일반 블루투스 1
                        connectDevice(data);
                    } else if (deviceType == BluetoothDevice.DEVICE_TYPE_LE) {    //ble 블루투스 2
                        if (this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                            connectLeDevice(data);
                        } else {
                            Toast.makeText(this, "BLE is not supported", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                }
                break;

            case REQUEST_ENABLE_BT: //블루투스비활성화시
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    mBleClasicService = new BluetoothService(this, mHandler);
                } else {
                    //Log.d( TAG, "BT not enabled" );
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {   //상단 메뉴 클릭시

        Intent serverIntent = null;
        switch( item.getItemId() )
        {
            case R.id.connect_scan:
                // Launch the DeviceListActivity to see devices and do scan
                serverIntent = new Intent( this, DeviceListActivity.class );
                startActivityForResult( serverIntent, REQUEST_CONNECT_DEVICE );
                return true;

            case R.id.discoverable:
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
        }
        return false;
    }
    private final void setStatus( int resId ){
        //final ActionBar actionBar = getActionBar();
        //actionBar.setSubtitle( resId );
    }

    private final void setStatus( CharSequence subTitle ){
        //final ActionBar actionBar = getActionBar();
        //actionBar.setSubtitle( subTitle );
    }
    private void initUi() {
        btn_reset = findViewById(R.id.btn_reset);
        btn_edit = findViewById(R.id.btn_edit);
        btn_insert = findViewById(R.id.btn_insert);
        btn_start = findViewById(R.id.btn_start);
        btn_stop = findViewById(R.id.btn_stop);
        btn_continue = findViewById(R.id.btn_continue);
        btn_washer = findViewById(R.id.btn_washer);
        btn_drain = findViewById(R.id.btn_drain);
        btn_vent = findViewById(R.id.btn_vent);
        btn_conn = findViewById(R.id.btn_conn);
        btn_ble_conn = findViewById(R.id.btn_ble_conn);

        tv_total_cnt = findViewById(R.id.tv_total_cnt);
        tv_top_state = findViewById(R.id.tv_top_state);
        tv_top_volt = findViewById(R.id.tv_top_volt);
        tv_top_am = findViewById(R.id.tv_top_am);
        tv_time = findViewById(R.id.tv_time);
        tv_wm = findViewById(R.id.tv_wm);
        tv_fm = findViewById(R.id.tv_fm);
        tv_dm = findViewById(R.id.tv_dm);

        et_set_top_cnt = findViewById(R.id.et_set_top_cnt);
        et_set_pump_cnt = findViewById(R.id.et_set_pump_cnt);
        et_set_volt = findViewById(R.id.et_set_volt);
        et_set_volt_dec = findViewById(R.id.et_set_volt_dec);
        et_set_am = findViewById(R.id.et_set_am);
        et_set_am_dec = findViewById(R.id.et_set_am_dec);
        et_set_on = findViewById(R.id.et_set_on);
        et_set_off = findViewById(R.id.et_set_off);
        et_set_delay = findViewById(R.id.et_set_delay);
        et_set_flooding = findViewById(R.id.et_set_flooding);
        TimerTask mTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    public void run() {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일 EE요일 aa hh시 mm분 ss초");
                        tv_time.setText(sdf.format(new Date(System.currentTimeMillis())));
                    }
                });
            }
        };
        Timer mTimer = new Timer(true);
        mTimer.schedule(mTask, 100, 1000);
        //저장된 값을 불러오기 위해 같은 네임파일을 찾음.
        SharedPreferences sf = getSharedPreferences("wp",MODE_PRIVATE);
        String setVal = sf.getString("set_val","");

        btn_edit.setEnabled(true);
        btn_insert.setEnabled(false);
        et_set_top_cnt.setEnabled(false);
        et_set_pump_cnt.setEnabled(false);
        et_set_volt.setEnabled(false);
        et_set_am.setEnabled(false);
        et_set_volt_dec.setEnabled(false);
        et_set_am_dec.setEnabled(false);
        et_set_on.setEnabled(false);
        et_set_off.setEnabled(false);
        et_set_delay.setEnabled(false);
        et_set_flooding.setEnabled(false);
    }
    private void btnOnClickEvent() {
        btn_ble_conn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent serverIntent = new Intent( MainActivity.this, DeviceListActivity.class );
                startActivityForResult( serverIntent, REQUEST_CONNECT_DEVICE );
            }
        });
        btn_reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeToBle("$CRESET");
            }
        });
        btn_edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(writeToBle("$CEDIT")){
                    btn_edit.setEnabled(false);
                    btn_insert.setEnabled(true);
                    et_set_top_cnt.setEnabled(true);
                    et_set_pump_cnt.setEnabled(true);
                    et_set_volt.setEnabled(true);
                    et_set_am.setEnabled(true);
                    et_set_volt_dec.setEnabled(true);
                    et_set_am_dec.setEnabled(true);
                    et_set_on.setEnabled(true);
                    et_set_off.setEnabled(true);
                    et_set_delay.setEnabled(true);
                    et_set_flooding.setEnabled(true);
                }
            }
        });
        btn_insert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(et_set_top_cnt.isEnabled()){
                    String etTopCnt = String.valueOf(et_set_top_cnt.getText());
                    if(etTopCnt.isEmpty()){
                        Toast.makeText(MainActivity.this, "전체 회차를 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_top_cnt.requestFocus();
                        return;
                    }
                    String etPump = String.valueOf(et_set_pump_cnt.getText());
                    if(etPump.isEmpty()){
                        Toast.makeText(MainActivity.this, "On Off 회차를 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_pump_cnt.requestFocus();
                        return;
                    }
                    String etOn = String.valueOf(et_set_on.getText());
                    if(etOn.isEmpty()){
                        Toast.makeText(MainActivity.this, "On Time 을 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_on.requestFocus();
                        return;
                    }
                    String etOff = String.valueOf(et_set_off.getText());
                    if(etOff.isEmpty()){
                        Toast.makeText(MainActivity.this, "Off Time 을 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_off.requestFocus();
                        return;
                    }
                    String etDelay = String.valueOf(et_set_delay.getText());
                    if(etDelay.isEmpty()){
                        Toast.makeText(MainActivity.this, "지연 Time 을 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_delay.requestFocus();
                        return;
                    }
                    String etFlood = String.valueOf(et_set_flooding.getText());
                    if(etFlood.isEmpty()){
                        Toast.makeText(MainActivity.this, "침수 회차를 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_flooding.requestFocus();
                        return;
                    }
                    String etVolt = String.valueOf(et_set_volt.getText());
                    if(etVolt.isEmpty()){
                        Toast.makeText(MainActivity.this, "Volt 소수점 이상을 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_volt.requestFocus();
                        return;
                    }
                    String etVoltDec = String.valueOf(et_set_volt_dec.getText());
                    if(etVoltDec.isEmpty()){
                        Toast.makeText(MainActivity.this, "Volt 소수점 이하를 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_volt_dec.requestFocus();
                        return;
                    }
                    String etAm = String.valueOf(et_set_am.getText());
                    if(etAm.isEmpty()){
                        Toast.makeText(MainActivity.this, "암페어 소수점 이상을 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_am.requestFocus();
                        return;
                    }
                    String etAmDec = String.valueOf(et_set_am_dec.getText());
                    if(etAmDec.isEmpty()){
                        Toast.makeText(MainActivity.this, "암페어 소수점 이하를 입력해 주세요.", Toast.LENGTH_LONG).show();
                        et_set_am_dec.requestFocus();
                        return;
                    }

                    String saveVal = etTopCnt+sepa+etPump+sepa+etOn+sepa+etOff+sepa+etDelay+sepa+etFlood+sepa+etVolt+"."+etVoltDec+sepa+etAm+"."+etAmDec;

                    if(writeToBle("$S"+saveVal)){
                        btn_edit.setEnabled(true);
                        btn_insert.setEnabled(false);
                        et_set_top_cnt.setEnabled(false);
                        et_set_pump_cnt.setEnabled(false);
                        et_set_volt.setEnabled(false);
                        et_set_am.setEnabled(false);
                        et_set_volt_dec.setEnabled(false);
                        et_set_am_dec.setEnabled(false);
                        et_set_on.setEnabled(false);
                        et_set_off.setEnabled(false);
                        et_set_delay.setEnabled(false);
                        et_set_flooding.setEnabled(false);
                    }
                    /*SharedPreferences sf = getSharedPreferences("wp",MODE_PRIVATE);
                    SharedPreferences.Editor editor = sf.edit();
                    editor.putString("set_val",saveVal);
                    editor.commit();*/
                }
            }
        });
        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d("===",""+mBleClasicService.getState());
                writeToBle("$CSTART");
            }
        });
        btn_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeToBle("$CPAUSE");
            }
        });
        btn_continue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeToBle("$CRESUME");
            }
        });
        btn_washer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeToBle("$TWASHER");
            }
        });
        btn_drain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeToBle("$TINPUT");
            }
        });
        btn_vent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeToBle("$TOUTPUT");
            }
        });
    }
    //블루투스 LE 핸들러 BleManager에서 받는곳
    class ServiceHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {

            switch(msg.what) {
                // Bluetooth state changed
                case BleManager.MESSAGE_STATE_CHANGE:
                    // Bluetooth state Changed
                    Log.d(TAG, "Service - MESSAGE_STATE_CHANGE: " + msg.arg1);

                    switch (msg.arg1) {
                        case BleManager.STATE_NONE:
                            //mActivityHandler.obtainMessage(Constants.MESSAGE_BT_STATE_INITIALIZED).sendToTarget();
                            mHandler.obtainMessage(Constants.MESSAGE_BT_STATE_INITIALIZED).sendToTarget();
                            break;

                        case BleManager.STATE_CONNECTING:
                            mHandler.obtainMessage(Constants.MESSAGE_BT_STATE_CONNECTING).sendToTarget();
                            break;

                        case BleManager.STATE_CONNECTED:
                            mHandler.obtainMessage(16).sendToTarget();
                            break;

                        case BleManager.STATE_IDLE:
                            mHandler.obtainMessage(Constants.MESSAGE_BT_STATE_INITIALIZED).sendToTarget();
                            break;
                    }
                    break;

                // If you want to send data to remote
                case BleManager.MESSAGE_WRITE:
                    Log.d(TAG, "Service - MESSAGE_WRITE: ");
                    String message = (String) msg.obj;
                    /*if(message != null && message.length() > 0)
                        sendMessageToDevice(message);*/
                    break;

                // Received packets from remote
                case BleManager.MESSAGE_READ:
                    String strMsg = (String) msg.obj;
                    // send bytes in the buffer to activity
                    //Log.d(TAG, "Service - MESSAGE_READ: "+strMsg);
                    if(strMsg != null && strMsg.length() > 0) {
                        //mActivityHandler.obtainMessage(Constants.MESSAGE_READ_CHAT_DATA, strMsg).sendToTarget();
                        mHandler.obtainMessage(Constants.MESSAGE_READ_CHAT_DATA, strMsg).sendToTarget();
                    }
                    break;

                case BleManager.MESSAGE_DEVICE_NAME:
                    Log.d(TAG, "Service - MESSAGE_DEVICE_NAME: ");

                    // save connected device's name and notify using toast
                    String deviceAddress = msg.getData().getString(Constants.SERVICE_HANDLER_MSG_KEY_DEVICE_ADDRESS);
                    String deviceName = msg.getData().getString(Constants.SERVICE_HANDLER_MSG_KEY_DEVICE_NAME);
                    mConnectedDeviceName = deviceName;
                    if(deviceName != null && deviceAddress != null) {
                        // Remember device's address and name
                        /*mConnectionInfo.setDeviceAddress(deviceAddress);
                        mConnectionInfo.setDeviceName(deviceName);*/
                        //setStatus( getString(R.string.title_connected_to, deviceName) );
                        Toast.makeText(getApplicationContext(),
                                "Connected to " + deviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;

                case BleManager.MESSAGE_TOAST:
                    Log.d(TAG, "Service - MESSAGE_TOAST: ");

                    Toast.makeText(getApplicationContext(),
                            msg.getData().getString(Constants.SERVICE_HANDLER_MSG_KEY_TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;

            }	// End of switch(msg.what)

            super.handleMessage(msg);
        }
    }	// End of class MainHandler
}
