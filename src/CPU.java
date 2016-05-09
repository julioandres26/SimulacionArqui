import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class CPU implements Runnable {

    public int id, reloj, pc, pc_contexto, reloj_fallo, quantum;
    public int tam_mem_princ = 128 + 256; // 128 bytes de memoria compartida

    //    public boolean fallo_cache = false;
    public boolean terminado;

    CyclicBarrier barrera;

//    public String hiloMIPS_actual = "";
//    public String hiloMIPS_contexto = "";

    int cant_hilos;
    int hilo_actual;
    public int contexto[][] = new int[cant_hilos][33]; //guarda los registros y el pc de un hilo.

    public int ir[] = new int[4];
    public int registros[] = new int[32];
    public int memoria_principal[] = new int[tam_mem_princ];
    public int etiquetas_cache[] = new int[4]; //arreglo de las etiquetas. Inicializados en -1.
    public int cache[][][] = new int[4][4][4]; //índice, parabra, byte.
//    public int registros_contexto[] = new int[32];
    List<Integer[]> registros_contexto = new ArrayList<Integer[]>();

    public CPU(int id, int quantum, File[] hilos, CyclicBarrier barrera) {
        this.id = id;
        this.quantum = quantum;
        this.barrera = barrera;

        cant_hilos = hilos.length;

        terminado = false;

        reloj = pc = pc_contexto = 0;

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
            etiquetas_cache[i] = -1;
        }

//        for (int i = 0; i < 4; i++) {
//            etiquetas_cache[i] = -1;
//        }

        cargarHilosMemoria(hilos);
    }

    public void cargar_instruccion(int pc) {
//        pc = 200; //prueba

        int bloque = pc / 16; // System.out.println("Bloque = " + bloque + "\nPC = " + pc);
        int indice = bloque % 4;

        int resultado_previo = pc % 16;
        int palabra = resultado_previo / 4;

//        for (int k = 0; k < 128 + 256; k++) { //pueba
//            memoria_principal[k] = k;
//        }

        if (etiquetas_cache[indice] == bloque) { //hit de cache
//            fallo_cache = false;
            for (int j = 0; j < 4; j++) {
                ir[j] = cache[indice][palabra][j]; //copia al registro IR la intrucción codificada.
            }
        } else { //fallo de cache
//            fallo_cache = true;
//            reloj_fallo = reloj;
            int direccion_memoria = bloque * 16; //dirección en la que comienza el bloque que hay que cargar a cache.
            int i = direccion_memoria;

            for (int j = 0; j < 4; j++) { //palabra
                for (int k = 0; k < 4; k++) { //byte
                    cache[indice][j][k] = memoria_principal[i + (j * 4) + k]; //copia a caché el bloque de la instrucción.
                }
            }

            for (int j = 0; j < 4; j++) {
                ir[j] = cache[indice][palabra][j]; //copia al registro IR la intrucción codificada.
            }

            etiquetas_cache[indice] = bloque;

            fallo_cache(); //le dice al hilo padre que ya terminó su ejecución (por 16 ciclos).
        }
    }

    public void fallo_cache() {
        System.out.println("¡Fallo de cache!");
        for (int i = 0; i < 16; i++) {
            try {
                barrera.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                //...
            }
        }
    }

    public void ejecutar_instruccion() {
        switch (ir[0]) {
            case 2:
                JR(ir[1]);
                break;
            case 3:
                JAL(ir[3]);
                break;
            case 4:
                BEQZ(ir[1], ir[3]);
                break;
            case 5:
                BNEZ(ir[1], ir[3]);
                break;
            case 8:
                DADDI(ir[1], ir[2], ir[3]);
                break;
            case 12:
                DMUL(ir[1], ir[2], ir[3]);
                break;
            case 14:
                DDIV(ir[1], ir[2], ir[3]);
                break;
            case 32:
                DADD(ir[1], ir[2], ir[3]);
                break;
            case 34:
                DSUB(ir[1], ir[2], ir[3]);
                break;
            case 63:
                FIN();
                break;
            default:
                System.out.println("¡Codigo de operacion invalido!");
                break;
        }
        quantum--; //el quantum se resta cuando se ejecuta una instrucción.
    }

//    public boolean termino() {
//        boolean respuesta;
//        reloj++;
//        if (fallo_cache) {
//            if (reloj == (reloj_fallo + 16)) {
//                respuesta = true;
//            } else {
//                respuesta = false;
//            }
//        } else {
//            respuesta = true;
//        }
//        return respuesta;
//    }

    public void cambio_contexto() { //cambia de contexto al hilo actual por el siguiente en la cola (actual+1).
//        String hiloMIPS_temp;
        int pc_temp;
        int registros_temp[] = new int[32];

//        hiloMIPS_temp = hiloMIPS_contexto;
        pc_temp = contexto[(hilo_actual+1)%cant_hilos][0]; //pc del siguiente hilo en la "cola de espera".
        for (int i = 1; i < 33; i++) {
            registros_temp[i] = contexto[(hilo_actual+1)%cant_hilos][i]; //registros del siguiente hilo en la cola.
        }

        contexto[hilo_actual][0] = pc;
        for (int i = 1; i < 33; i++) {
            contexto[hilo_actual][i] = registros[i];
        }

//        hiloMIPS_actual = hiloMIPS_temp;
        pc = pc_temp;
        for (int i = 0; i < 32; i++) {
            registros[i] = registros_temp[i];
        }
    }

    public void DADDI(int RY, int RX, int n) {
        registros[RX] = registros[RY] + n;
        pc += 4;
    }

    public void DADD(int RY, int RZ, int RX) {
        registros[RX] = registros[RY] + registros[RZ];
        pc += 4;
    }

    public void DSUB(int RY, int RZ, int RX) {
        registros[RX] = registros[RY] - registros[RZ];
        pc += 4;
    }

    public void DMUL(int RY, int RZ, int RX) {
        registros[RX] = registros[RY] * registros[RZ];
        pc += 4;
    }

    public void DDIV(int RY, int RZ, int RX) {
        registros[RX] = registros[RY] / registros[RZ];
        pc += 4;
    }

    public void BEQZ(int RX, int ETIQ) {
        pc += 4;
        if (registros[RX] == 0) {
            pc += (ETIQ * 4);
        }
    }

    public void BNEZ(int RX, int ETIQ) {
        pc += 4;
        if (registros[RX] != 0) {
            pc += (ETIQ * 4);
        }
    }

    public void JAL(int n) {
        pc += 4;
        registros[31] = pc;
        pc += n;
    }

    public void JR(int RX) {
        pc += 4;
        pc = registros[RX];
    }

    public void FIN() {
        terminado = true;
        System.out.println("FIIIIIIIIIIIIIIIIIIIIIIIN!!!!!!!!!!!!!!!!!!!!!!");
    }

    public void imprimir_registros() {
        System.out.println("\nRegistros de CPU " + id + ":");
        for (int i = 0; i < 32; i++) {
            if(registros[i] != 0)
                System.out.println("R" + i + " " + registros[i]);
        }
        System.out.println();
    }

//    public void cambiar_variable_compartida(CPU cpu) {
//        cpu.variable_compartida += 1;
//    }

    public boolean procesamientoTerminado() {
        return terminado;
    }

    private void cargarHilosMemoria(File[] pathHilos){
//    public void cargarHilosMemoria() {
        int inicioMemoria = 128;

        for (int i = i; i <= pathHilos.length; i++){

        }
        Path filePath = pathHilos[0].toPath() ;
        System.out.println(filePath);
        //Path filePath = Paths.get("G:/Sharon/Cursos/Arquitectura de Computadoras/Proyecto/HILOS 1era Parte/2.txt");
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
        } catch (IOException e) {
            //...
        }


    }

    public void run() {
        System.out.println("EXITO");
        System.out.println(Thread.currentThread().getName());

//        hiloMIPS_actual = "1";
        pc = 128;

        while ((quantum > 0) && !terminado) {
            cargar_instruccion(pc);
            ejecutar_instruccion();

            System.out.print("IR = ");
            for (int i = 0; i < 4; i++)
                System.out.print(ir[i] + " ");
            System.out.println();

            try {
                barrera.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                //...
            }

//            quantum--;
            System.out.println("quantum = " + quantum);
        }

        //cambio_contexto(hiloMIPS_actual);

//        for (int i = 0; i <= 5; i++){
//            System.out.println(i);
//            try {
//                barrera.await();
//            } catch (InterruptedException | BrokenBarrierException e) {
//                //...
//            }
//        }
        terminado = true;
    }
}

//import java.io.*;
//import java.nio.charset.Charset;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Scanner;
//import java.util.concurrent.CyclicBarrier;
//import java.util.concurrent.BrokenBarrierException;
//import java.lang.Character;
//
//import static java.lang.Character.getNumericValue;
//
//
///**
// * Created by Julio on 5/2/16.
// */
//public class CPU implements Runnable{
//    public int id, reloj, pc, reloj_fallo, quantum;
//    public int ir[] = new int[4];
//    public boolean fallo_cache = false;
//
//    boolean terminado;
//
//    CyclicBarrier barrera;
//
//    public int variable_compartida = 0;
//
//    public int tam_mem_princ = 128 + 256;
//    public int tam_cache = 64; //4 bloques * 4 palabras * 4 bytes
//
//    public int registros[] = new int[32];
//    public int memoria_principal[] = new int[tam_mem_princ];
//    public int etiquetas_cache[] = new int[4]; //Arreglo de las etiquetas. Inicializados en -1.
//    //primer subindice es el Indice
//    //segundo subindice es la Palabra
//    //tercer subindice es el Byte(es un entero)
//    public int cache[][][] = new int[4][4][4];
//
//    public CPU(int id, int quantum, CyclicBarrier barrera) {
//        this.id = id;
//        this.quantum = quantum;
//        this.barrera = barrera;
//
//        terminado = false;
//
//        reloj = 0;
//        pc = 0;
//
//        for (int i = 0; i < 32; i++) {
//            registros[i] = 0;
//        }
//
//        for (int i = 0; i < tam_mem_princ; i++) {
//            memoria_principal[i] = 0;
//        }
//
//        for (int i = 0; i < 4; i++) {
//            for (int j = 0; j < 4; j++) {
//                for (int k = 0; k < 4; k++) {
//                    cache[i][j][k] = 0;
//                }
//            }
//        }
//
//        for (int i = 0; i < 4; i++) {
//            etiquetas_cache[i] = -1;
//        }
//    }
//
//    public void ejecutar_instruccion(){
//        pc = 200; //prueba
//
//        int bloque = pc / 16;
//        int indice = bloque % 4;
//
//        int resultado_previo = pc % 16;
//        int palabra = resultado_previo / 4;
//
//        for (int k = 0; k < 128 + 256; k++) {
//            memoria_principal[k] = k;
//        }
//
//        if (etiquetas_cache[indice] == bloque) { //hit cache
//            fallo_cache = false;
//            for (int j = 0; j < 4; j++)
//                ir[j] = cache[indice][palabra][j];
//        } else { //fallo cache
//            fallo_cache = true;
//            reloj_fallo = reloj;
//            int direccion_memoria = 128 + (bloque * 16);
//            int i = direccion_memoria;
//            for (int j = 0; j < 4; j++) {
//                for (int k = 0; k < 4; k++)
//                    cache[indice][j][k] = memoria_principal[i + (j * 4) + k];
//            }
//            for(int j = 0; j < 4; j++)
//                ir[j] = cache[indice][palabra][j];
//
//            for(int m = 0; m < 16; m++){
//
//            }
//        }
//    }
//
//    public boolean termino(){
//        boolean respuesta;
//        reloj++;
//        if (fallo_cache){
//            if ( reloj == (reloj_fallo + 16)){
//                respuesta = true;
//            } else {
//                respuesta = false;
//            }
//        } else {
//            respuesta = true;
//        }
//        return  respuesta;
//    }
//
//    public void DADDI(int RX, int RY, int n) {
//        registros[RX] = registros[RY] + n;
//        pc += 4;
//    }
//
//    public void DADD(int RX, int RY, int RZ) {
//        registros[RX] = registros[RY] + registros[RZ];
//        pc += 4;
//    }
//
//    public void DSUB(int RX, int RY, int RZ) {
//        registros[RX] = registros[RY] * registros[RZ];
//        pc += 4;
//    }
//
//    public void DMUL(int RX, int RY, int RZ) {
//        registros[RX] = registros[RY] - registros[RZ];
//        pc += 4;
//    }
//
//    public void DDIV(int RX, int RY, int RZ) {
//        registros[RX] = registros[RY] / registros[RZ];
//        pc += 4;
//    }
//
//    public void BEQZ(int RX, int ETIQ) {
//        if (registros[RX] == 0) {
//            pc += ETIQ;
//        } else {
//            pc += 4;
//        }
//    }
//
//    public void BNEZ(int RX, int ETIQ) {
//        if (registros[RX] != 0) {
//            pc += ETIQ;
//        } else {
//            pc += 4;
//        }
//    }
//
//    public void JAL(int n) {
//        registros[31] = pc;
//        pc += n;
//    }
//
//    public void JR(int RX) {
//        pc = registros[RX];
//    }
//
//    public void FIN() {
//        // Detiene el programa
//    }
//
//    public void imprimir_registros() {
//        System.out.println("Registros de CPU " + id + ":\n");
//        for (int i = 0; i < 32; i++) {
//            System.out.print(registros[i] + " ");
//        }
//        System.out.println();
//    }
//
//    public void cambiar_variable_compartida(CPU cpu) {
//        cpu.variable_compartida += 1;
//    }
//
//    public boolean procesamientoTerminado(){
//        return terminado;
//    }
//
////    public void cargarHilosMemoria(String[] pathHilos){
//    public void cargarHilosMemoria() {
//        int inicioMemoria = 128;
//
//        Path filePath = Paths.get("/Users/Julio/Downloads/HILOS 1era Parte/1.txt");
//        try {
//            Scanner scanner = new Scanner(filePath);
//            while (scanner.hasNext()) {
//                if (scanner.hasNextInt()) {
//                    memoria_principal[inicioMemoria] = scanner.nextInt();
//                    inicioMemoria++;
//                } else {
//                    scanner.next();
//                }
//            }
//            }catch(IOException e){
//                //...
//            }
//        }
//
//
//    public void run(){
//        System.out.println("EXITO");
//        System.out.println(Thread.currentThread().getName());
//
////        for (int i = 0; i <= 5; i++){
////            System.out.println(i);
////            try {
////                barrera.await();
////            } catch (InterruptedException | BrokenBarrierException e) {
////                //...
////            }
////        }
//        cargarHilosMemoria();
//        terminado = true;
//
//    }
//
//}

