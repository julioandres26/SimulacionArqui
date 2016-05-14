import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class CPU implements Runnable {

    public int id, reloj, pc, pc_contexto, reloj_fallo, quantum_original, quantum;
    public int tam_mem_princ = 128 + 256; // 128 bytes de memoria compartida

    public boolean terminado;

    CyclicBarrier barrera;

    int cant_hilos;
    int hilo_actual = 0;
    public int contexto[][]; //guarda los registros y el pc de un hilo.
    public boolean hilos_terminados[];

    public int ir[] = new int[4];
    public int registros[] = new int[32];
    public int memoria_principal[] = new int[tam_mem_princ];
    public int etiquetas_cache[] = new int[4]; //arreglo de las etiquetas. Inicializados en -1.
    public int cache[][][] = new int[4][4][4]; //índice, parabra, byte.

    public CPU(int id, int quantum, List<File> hilos, CyclicBarrier barrera) {
        this.id = id;
        this.quantum = quantum;
        this.quantum_original = quantum;
        this.barrera = barrera;

        pc = 128;

        cant_hilos = hilos.size();

        contexto = new int[cant_hilos][33];
        hilos_terminados = new boolean[cant_hilos];

        for (int i = 0; i < cant_hilos; i++){
            hilos_terminados[i] = false;
        }

        terminado = false;

        reloj = pc_contexto = 0;

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
        cargarHilosMemoria(hilos);
    }

    public void cargar_instruccion(int pc) {

        int bloque = pc / 16; // System.out.println("Bloque = " + bloque + "\nPC = " + pc);
        int indice = bloque % 4;

        int resultado_previo = pc % 16;
        int palabra = resultado_previo / 4;

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

    public void cambio_contexto() { //cambia de contexto al hilo actual por el siguiente en la cola (actual+1).
        int siguiente_hilo = (hilo_actual+1)%cant_hilos;

        while (hilos_terminados[siguiente_hilo] == true) {
            siguiente_hilo = (siguiente_hilo+1)%cant_hilos;
        }

        contexto[hilo_actual][32] = pc;
        for (int i = 0; i < 32; i++) {
            contexto[hilo_actual][i] = registros[i];
        }

        pc = contexto[siguiente_hilo][32];
        for (int i = 0; i < 32; i++) {
            registros[i] = contexto[siguiente_hilo][i];
        }

        hilo_actual = siguiente_hilo;
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
        hilos_terminados[hilo_actual] = true;
        imprimir_registros();
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

    public boolean procesamientoTerminado() {
        int j = 0;
        if (terminado == true){
            try {
                barrera.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                //...
            }
        } else{
            try {
                barrera.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                //...
            }
            for (int i = 0; i < cant_hilos; i++){
                if (hilos_terminados[i] == true){
                    j++;
                }
            }
            if (j == cant_hilos){
                terminado = true;
            }
            return terminado;
        }
        return terminado;
    }

    private void cargarHilosMemoria(List<File> pathHilos){
        int inicioMemoria = 128;

        for (int i = 0; i < pathHilos.size(); i++){
            contexto[i][32] = inicioMemoria;
            Path filePath = pathHilos.get(i).toPath() ;
            System.out.println(filePath);
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
    }

    public void run() {
        System.out.println("EXITO");
        System.out.println(Thread.currentThread().getName());

        while (!procesamientoTerminado()){
                while (quantum > 0 && hilos_terminados[hilo_actual] == false){
                    cargar_instruccion(pc);
                    ejecutar_instruccion();

//                    System.out.print("IR = ");
//                    for (int i = 0; i < 4; i++)
//                        System.out.print(ir[i] + " ");
//                    System.out.println();

                    try {
                        barrera.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        //...
                    }
//                    System.out.println("quantum = " + quantum);
                }
                quantum = quantum_original;
                cambio_contexto();
        }
        terminado = true;
    }

}

