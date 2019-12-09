package com.andrew.memoappv3;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

class BluetoothConexion {
    // Debugging
    private static final String TAG = "MemoAppBluetooth";

    //Constantes de los mensajes del handler
    public static final int MSJ_CONEXION_OK = 1;
    public static final int MSJ_RX_DATO = 2;
    public static final int MSJ_TOAST = 5;
    public static final int NADA = -1;//para indicar que no se envia "nada" no se deja vacio porque el metodo requiere un int

    /*Identificador único universal (UUID) tomado de "Create RFCOMM Socket to Service Record - UUID"
    https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html#createRfcommSocketToServiceRecord%28java.util.UUID%29
    donde se menciona que suele funcionar con los modulos bluetooth seriales*/
    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mBtAdapter;
    private final Handler mHandler;
    private ThreadConexion mThreadConexion;
    private ThreadTransmision mThreadTransmision;


    /**
     * Constructor. Prepara una nueva comunicación Bluetooth.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public BluetoothConexion(Context context, Handler handler) {
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = handler;
    }



    /**
     * Inicializa el hilo "ThreadConexion" para iniciar una conexión al dispositivo remoto.
     *
     * @param device El "BluetoothDevice" al que se conectara
     *
     */
    public synchronized void conectar(BluetoothDevice device) {
        // Cancelamos cualquier otro hilo que este intentando HACER UNA CONEXIÓN
        if (mThreadConexion != null) {
            mThreadConexion.cancel();
            mThreadConexion = null;
        }

        // Cancelelamos cualquier otro hilo que este MANEJANDO UNA CONEXIÓN Y Transmision
        if (mThreadTransmision != null) {
            mThreadTransmision.cancel();
            mThreadTransmision = null;
        }

        // Se crea e inicializa el hilo para conectar con el dispositivo Bluetooth
        mThreadConexion = new ThreadConexion(device);
        mThreadConexion.start();//Hace que este hilo comience la ejecución,  la maquina virtual de Java Virtual
        // llama el método "run" de este hilo (El thread que establece la comunicación)
    }



    /**
     * Inicializa el hilo de transmición "ThreadTransmision" para manejar la conexión y comunicación Bluetooth
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been iniciarComunicación
     */
    public synchronized void iniciarComunicación(BluetoothSocket socket, BluetoothDevice device) {
        //Enviamos mensaje a la UI para indicar que se realizo correctamente la conexionBT
        mHandler.obtainMessage(MSJ_CONEXION_OK).sendToTarget();

        // Cancelamos el hilo que realizo la conexión
        if (mThreadConexion != null) {
            mThreadConexion.cancel();
            mThreadConexion = null;
        }

        // Cancelelamos cualquier otro hilo que este MANEJANDO UNA CONEXIÓN Y Transmision
        if (mThreadTransmision != null) {
            mThreadTransmision.cancel();
            mThreadTransmision = null;
        }

        // Se crea e inicializa el hilo para manejar la conexion y relizar la transmición
        // Para iniciar el thread se instancia un objeto y se llama el metodo "start".
        mThreadTransmision = new ThreadTransmision(socket);
        mThreadTransmision.start();
    }



    /**
     * Detener todos los hilos
     */
    public synchronized void stop() {

        if (mThreadConexion != null) {
            mThreadConexion.cancel();
            mThreadConexion = null;
        }

        if (mThreadTransmision != null) {
            mThreadTransmision.cancel();
            mThreadTransmision = null;
        }
    }



    /**
     * Indica que el intento de conexión fallo y notifica a la actividad con la interfaz de usuario (MainActivity).
     */
    private void connectionFailed() {
        // Envia un mensaje de fallo de vuelta a la Actividad principal
        Message msg = mHandler.obtainMessage(MSJ_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "No se pudo conectar al dispositivo");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }



    private void connectionLost() {
        // Envia un mensaje de fallo de vuelta a la Actividad principal
        Message msg = mHandler.obtainMessage(MSJ_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Se perdio la conexión con el dispositivo");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }



    //Declaración de la clase subclase de Thread que CONECTA CON EL DISPOSITIVO BLUETOOTH.
    // Esta subclase sobreescribe el método "run" de la clase Thread.
    // Para iniciar el thread se instancia un objeto y se llama el metodo "star".
    /**
     * Este hilo corre mientras se realiza la conexión con el dispositivo remoto
     * Se ejecuta hasta que la conexión al final se realice o falle
     */
    private class ThreadConexion extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ThreadConexion(BluetoothDevice device) {
            // Usa un objeto temporal que sera luego asignado a mmSocket
            // dado que mmSocket es "final".
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Obtiene un BluetoothSocket para conectar con el dispositivo Bluetooth dado.
                // BT_MODULE_UUID es el identificador universal unico que tambien esta en el Arduino (Servidor).
                tmp = device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
            } catch (IOException e) {
                //La creacción del Socket fallo
            }
            mmSocket = tmp;
        }
        //Metodo "run" del ThreadConexion
        public void run() {

            try {
                // Se conecta con el dispositivo remoto a traves del socket.
                // Este metodo espera hasta que conecto efectivamente o arroje una excepción.
                mmSocket.connect();
            } catch (IOException connectException) {
                // No fue posible conectar, cierra el socket y retorna.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    //No se pudo cerrar el socket
                }
                connectionFailed();
                return;
            }

            // Resetea el hilo ThreadConexion pues ya cumplio su objetivo
            //Con "synchronized"  bloquea la clase para que no se le realicen modificaciones mientras lo cierra
            synchronized (BluetoothConexion.this) {
                mThreadConexion = null;
            }

            // Start the iniciarComunicación thread
            iniciarComunicación(mmSocket, mmDevice);
        }

        //Metodo "cancel" del ThreadConexion
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                //Error al cerrar el socket
            }
        }
    }



    //Declaración de la clase subclase de Thread que MANEJA LA TRASMICIÓN SERIAL.  Esta subclase sobreescribe el
    // método "run" de la clase Thread.
    // Para iniciar el thread se instancia un objeto y se llama el metodo "start".
    /**
     * Este hilo corre mientras se esta conectado con el dispositivo remoto.
     * Maneja las transmisiones entrantes.
     */
    private class ThreadTransmision extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;

        public ThreadTransmision(BluetoothSocket socket) {
            //Creado hilo ThreadTransmision
            mmSocket = socket;
            InputStream tmpIn = null;

            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                //Socket temporal de entrada no creado
            }

            mmInStream = tmpIn;
        }

        //Metodo "run" del ThreadTransmision
        public void run() {
            ///Iniciado hilo "ThreadTransmision"
            byte[] buffer = new byte[1024];
            int bytes;

            // Mantiene escuchando el flujo de entrada
            while (mThreadTransmision!=null) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(MSJ_RX_DATO, bytes, NADA, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    //Se perdio la conexión
                    connectionLost();
                    break;//Salimos del ciclo while lo que cerrara el hilo, antes no tenia esto y quedaba bloqueado al perderse la conexión
                }
            }
        }

        //Metodo "cancel" del ThreadTransmision
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                //Error al cerrar el socket
            }
        }
    }

}
