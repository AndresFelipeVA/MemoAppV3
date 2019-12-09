package com.andrew.memoappv3;

import androidx.lifecycle.ViewModel;

import java.util.Random;

public class BotonesViewModel extends ViewModel {
    //Guarda los numeros de los tres botones
    Random r = new Random();
    public int num_btArriba=r.nextInt(8);
    public int num_btCentral=r.nextInt(8);
    public int num_btAbajo=r.nextInt(8);

    public boolean conexionBT=false;
}