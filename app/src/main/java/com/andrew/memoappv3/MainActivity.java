package com.andrew.memoappv3;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import java.util.Random;


public class MainActivity extends AppCompatActivity {
    Button btCentral, btArriba, btAbajo;//Botones circulares con numeros y colores
    Button btBluetooth;//Boton para conectar el Bluetooth
    GradientDrawable fondoArriba;
    GradientDrawable fondoCentral;
    GradientDrawable fondoAbajo;

    Random r;
    boolean mUI_Activa =true;

    SoundPool soundPool;
    int sonido;//El sonido que se cargara en el soundPool


    final int[] colores= {Color.YELLOW, Color.GREEN, Color.BLUE, Color.MAGENTA, Color.RED, Color.GRAY, Color.WHITE, 0xffff8000};

    private final String mBTDeviceHardwareAddress = "98:D3:32:21:36:4E";

    private BluetoothConexion mBTConexion = null;

    BotonesViewModel mViewModel;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mViewModel = ViewModelProviders.of(this).get(BotonesViewModel.class);

        r = new Random();

        soundPool=new SoundPool(2, AudioManager.STREAM_MUSIC,0);
        sonido=soundPool.load(this,R.raw.correcto,1);

        btCentral = findViewById(R.id.btCentral);
        btArriba = findViewById(R.id.btArriba);
        btAbajo = findViewById(R.id.btAbajo);
        btBluetooth = findViewById(R.id.btBluetooth);

        fondoArriba= (GradientDrawable)btArriba.getBackground().mutate();
        fondoCentral= (GradientDrawable)btCentral.getBackground().mutate();
        fondoAbajo= (GradientDrawable)btAbajo.getBackground().mutate();

        if(mViewModel.conexionBT==true){
            conectarBTDevice(mBTDeviceHardwareAddress);
        }

        btArriba.setText(Integer.toString(mViewModel.num_btArriba+1));
        fondoArriba.setColor(colores[mViewModel.num_btArriba]);

        btCentral.setText(Integer.toString(mViewModel.num_btCentral+1));
        fondoCentral.setColor(colores[mViewModel.num_btCentral]);

        btAbajo.setText(Integer.toString(mViewModel.num_btAbajo+1));
        fondoAbajo.setColor(colores[mViewModel.num_btAbajo]);

    }

    public void btCentralClic(View view){
        avanzarSequencia();
        LayoutInflater inflater = getLayoutInflater();
        View toasInvisible = inflater.inflate(R.layout.toast_transparente,
                (ViewGroup) findViewById(R.id.toast_transparente_ID));

        Toast toast = Toast.makeText(getApplicationContext(),"", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 100);
        toast.setView(toasInvisible);
        //Sin necesidad del layout se puede cambiar el color del toast con:
        //toast.getView().setBackgroundColor(Color.RED);
        toast.show();
    }

    private void avanzarSequencia() {
        soundPool.play(sonido,1,1,1,0,1);

        mViewModel.num_btAbajo=mViewModel.num_btCentral;
        mViewModel.num_btCentral=mViewModel.num_btArriba;
        mViewModel.num_btArriba=r.nextInt(8);

        btArriba.setText(Integer.toString(mViewModel.num_btArriba+1));
        fondoArriba.setColor(colores[mViewModel.num_btArriba]);

        btCentral.setText(Integer.toString(mViewModel.num_btCentral+1));
        fondoCentral.setColor(colores[mViewModel.num_btCentral]);

        btAbajo.setText(Integer.toString( mViewModel.num_btAbajo+1));
        fondoAbajo.setColor(colores[ mViewModel.num_btAbajo]);
    }

    public void btBluetoothClic(View view){
        //btBluetooth.setVisibility(View.GONE);//Tambien puede ser View.INVISIBLE que sigue ocupando
        //un espacio en el Layout y para este caso es igual
        conectarBTDevice(mBTDeviceHardwareAddress);
    }


    /**
     * El Handler que obtiene información del hilo que corre con la comunicación Bluetooth
     */
    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if(msg.what==BluetoothConexion.MSJ_CONEXION_OK) {
                //Si entro aqui es porque conecto y se abrio el canal de comunicación correctamente
                btBluetooth.setText("Conectado");
                btBluetooth.setEnabled(false);//Ya no se necesita el boton
                mViewModel.conexionBT=true;
            }else if(msg.what==BluetoothConexion.MSJ_TOAST){
                //Ocurrio un error y se muestra el anuncio
                if(mUI_Activa ==true){//Pero solo si no es porque se cerro la aplicación (Completamente o por cambio de orientación)
                    btBluetooth.setText("Conexión Tapete");
                    btBluetooth.setEnabled(true);//Se necesita el boton
                    mViewModel.conexionBT=false;//La conexión se perdio, ya no debe intentar conectarse por si solo al reiniciar la actividad
                    Toast.makeText(getBaseContext(), msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                }
            }else{
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                btBluetooth.setText(readMessage);
                procesarMensaje(readMessage);//Se procesa el mensaje recibido
            }
        }
    };
    /**Metodo que procesa el mensaje recibido, identificando si el String recibido corresponde con el esperadp
     * lo que indica que se presiono sobre el lugar indicado por el boton central en pantalla
     */
    private void procesarMensaje(String readMessage) {
        if(readMessage.equals(btCentral.getText())){
            //btCentral.performClick();//Tambien se puede simular la presión del boton, pero igual se apaga la pantalla
            avanzarSequencia();//Deje este metodo en lugar de simular el clic por claridad
            Toast.makeText(getBaseContext(), "",
                    Toast.LENGTH_SHORT).show();
        }
    }


    private void conectarBTDevice(String mBTDeviceMAC) {
        /*Crea la clase para manejar la conexion y comunicación Bluetooth en la cual se implementan los respectivos hilos(thread)
        para no interrumpir la experiencia de usuario al realizar las tareas Bluetooth.
        En esta clase se encuentran los metodos para conectar y estar pendiente de la transmición serial que corren en thread separados,
        en cada uno de estos metodos se llama el metodo "star()" del thread que hace que el hilo respectivo comience la ejecución.*/
        mBTConexion = new BluetoothConexion(getBaseContext(), mHandler);

        BluetoothAdapter mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        //Se comprueba que el dispositivo (celular) tenga Bluetooth y lo tenga activado
        if (mBtAdapter == null) {
            //El dispositivo no soporta Bluetooth
            AvisoNoBTDialogFragment aviso1 = new AvisoNoBTDialogFragment();
            aviso1.show(getSupportFragmentManager(), "NoSoportaBT");

        } else {
            //Se comprueba que hayan dispositivos Bluetooth emparejados
            if (mBtAdapter.getBondedDevices().size() > 0) {
                BluetoothDevice device = mBtAdapter.getRemoteDevice(mBTDeviceMAC);
                // Se llama el metodo para conectar con el dispositivo Bluetooth
                mBTConexion.conectar(device);
            } else {
            //El Bluetooth se encuentra desactivado o no hay ningun dispositivo emparejado
                AvisoBTOffDialogFragment aviso2 = new AvisoBTOffDialogFragment();
                aviso2.show(getSupportFragmentManager(), "BTDesactivado");
            }
        }
    }



    //Nos aseguremos que se termine la comunicación Bluetooth y los thread creados
    @Override
    public void onDestroy() {
        super.onDestroy();
        mUI_Activa =false;
        if (mBTConexion != null) {
            mBTConexion.stop();
        }
    }
}
