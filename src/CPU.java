import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.BrokenBarrierException;
import java.lang.Character;

import static java.lang.Character.getNumericValue;


/**
 * Created by Julio on 5/2/16.
 */
public class CPU implements Runnable{
    public int id, reloj, pc, reloj_fallo, quantum;
    public int ir[] = new int[4];
    public boolean fallo_cache = false;

    boolean terminado;

    CyclicBarrier barrera;

    public int variable_compartida = 0;

    public int tam_mem_princ = 128 + 256;
    public int tam_cache = 64; //4 bloques * 4 palabras * 4 bytes

    public int registros[] = new int[32];
    public int memoria_principal[] = new int[tam_mem_princ];
    public int etiquetas_cache[] = new int[4]; //Arreglo de las etiquetas. Inicializados en -1.
    //primer subindice es el Indice
    //segundo subindice es la Palabra
    //tercer subindice es el Byte(es un entero)
    public int cache[][][] = new int[4][4][4];

    public CPU(int id, int quantum, CyclicBarrier barrera) {
        this.id = id;
        this.quantum = quantum;
        this.barrera = barrera;

        terminado = false;

        reloj = 0;
        pc = 0;

        for (int i = 0; i < 32; i++) {
            registros[i] = 0;
        }

        for (int i = 0; i < tam_mem_princ; i++) {
            memoria_principal[i] = 0;
        }

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                for (int k = 0; k < 4; k++) {
                    cache[i][j][k] = 0;
                }
            }
        }

        for (int i = 0; i < 4; i++) {
            etiquetas_cache[i] = -1;
        }
    }

    public void ejecutar_instruccion(){
        pc = 200; //prueba

        int bloque = pc / 16;
        int indice = bloque % 4;

        int resultado_previo = pc % 16;
        int palabra = resultado_previo / 4;

        for (int k = 0; k < 128 + 256; k++) {
            memoria_principal[k] = k;
        }

        if (etiquetas_cache[indice] == bloque) { //hit cache
            fallo_cache = false;
            for (int j = 0; j < 4; j++)
                ir[j] = cache[indice][palabra][j];
        } else { //fallo cache
            fallo_cache = true;
            reloj_fallo = reloj;
            int direccion_memoria = 128 + (bloque * 16);
            int i = direccion_memoria;
            for (int j = 0; j < 4; j++) {
                for (int k = 0; k < 4; k++)
                    cache[indice][j][k] = memoria_principal[i + (j * 4) + k];
            }
            for(int j = 0; j < 4; j++)
                ir[j] = cache[indice][palabra][j];

            for(int m = 0; m < 16; m++){

            }
        }
    }

    public boolean termino(){
        boolean respuesta;
        reloj++;
        if (fallo_cache){
            if ( reloj == (reloj_fallo + 16)){
                respuesta = true;
            } else {
                respuesta = false;
            }
        } else {
            respuesta = true;
        }
        return  respuesta;
    }

    public void DADDI(int RX, int RY, int n) {
        registros[RX] = registros[RY] + n;
        pc += 4;
    }

    public void DADD(int RX, int RY, int RZ) {
        registros[RX] = registros[RY] + registros[RZ];
        pc += 4;
    }

    public void DSUB(int RX, int RY, int RZ) {
        registros[RX] = registros[RY] * registros[RZ];
        pc += 4;
    }

    public void DMUL(int RX, int RY, int RZ) {
        registros[RX] = registros[RY] - registros[RZ];
        pc += 4;
    }

    public void DDIV(int RX, int RY, int RZ) {
        registros[RX] = registros[RY] / registros[RZ];
        pc += 4;
    }

    public void BEQZ(int RX, int ETIQ) {
        if (registros[RX] == 0) {
            pc += ETIQ;
        } else {
            pc += 4;
        }
    }

    public void BNEZ(int RX, int ETIQ) {
        if (registros[RX] != 0) {
            pc += ETIQ;
        } else {
            pc += 4;
        }
    }

    public void JAL(int n) {
        registros[31] = pc;
        pc += n;
    }

    public void JR(int RX) {
        pc = registros[RX];
    }

    public void FIN() {
        // Detiene el programa
    }

    public void imprimir_registros() {
        System.out.println("Registros de CPU " + id + ":\n");
        for (int i = 0; i < 32; i++) {
            System.out.print(registros[i] + " ");
        }
        System.out.println();
    }

    public void cambiar_variable_compartida(CPU cpu) {
        cpu.variable_compartida += 1;
    }

    public boolean procesamientoTerminado(){
        return terminado;
    }

//    public void cargarHilosMemoria(String[] pathHilos){
    public void cargarHilosMemoria() {
        int inicioMemoria = 128;

        Path filePath = Paths.get("/Users/Julio/Downloads/HILOS 1era Parte/1.txt");
        try {
            Scanner scanner = new Scanner(filePath);
            while (scanner.hasNext()) {
                if (scanner.hasNextInt()) {
                    memoria_principal[inicioMemoria] = scanner.nextInt();
                    inicioMemoria++;
                } else {
                    scanner.next();
                }
            }
            }catch(IOException e){
                //...
            }
        }


    public void run(){
        System.out.println("EXITO");
        System.out.println(Thread.currentThread().getName());

//        for (int i = 0; i <= 5; i++){
//            System.out.println(i);
//            try {
//                barrera.await();
//            } catch (InterruptedException | BrokenBarrierException e) {
//                //...
//            }
//        }
        cargarHilosMemoria();
        terminado = true;

    }

}
