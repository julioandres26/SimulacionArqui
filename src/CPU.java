import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CPU implements Runnable {

    public int id, pc, pc_contexto, quantum_original, quantum;
    public int tam_mem_princ = 128 + 256; // 128 bytes de memoria compartida + 256 de memoria de cada CPU
    public boolean terminado; //define si la CPU terminó de ejecutar todos sus hilos

    CyclicBarrier barrera; //barrera de sincronización de los CPU

    public List<File> hilos = new ArrayList<>(); //path de los hilos de esta CPU

    public int cant_hilos;
    public int hilo_actual; //hilo que está actualmente ejecutandose en el procesador
    public int contexto[][]; //guarda los registros y el pc de un hilo al sacarlo de ejecución
    public boolean hilos_terminados[]; //define cuáles hilos terminaron su ejecución
    public int reloj[]; //cantidad de ciclos que duró en ejecutarse cada hilo

    public int ir[] = new int[4]; //registro IR
    public int registros[] = new int[32]; //registros MIPS
    public int memoria_principal[] = new int[tam_mem_princ]; //384 bytes
    public int etiquetas_cache[] = new int[4]; //arreglo de las etiquetas inicializado en -1
    public int cache[][][] = new int[4][4][4]; //índice, parabra, byte.

    public CPU(int id, int quantum, List<File> hilos, CyclicBarrier barrera) {
        this.id = id;
        this.quantum = quantum;
        this.quantum_original = quantum;
        this.barrera = barrera;
        this.hilos = hilos;

        pc = 128; //inicio de la primera instrucción
        pc_contexto = 0;
        cant_hilos = hilos.size();
        hilo_actual = 0;

        contexto = new int[cant_hilos][33];
        hilos_terminados = new boolean[cant_hilos];
        reloj = new int[cant_hilos];

        terminado = false;

        for (int i = 0; i < cant_hilos; i++){
            hilos_terminados[i] = false;
            reloj[i] = 0;
        }

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

        cargar_hilos_memoria();
    }

    //Carga una instrucción de caché al IR.
    public void cargar_instruccion(int pc) {
        int bloque = pc / 16; //bloque de memoria donde está esa instrucción
        int indice = bloque % 4; //índice de la caché (mapeo directo)

        int resultado_previo = pc % 16;
        int palabra = resultado_previo / 4; //dirección de la instrucción dentro del bloque

        if (etiquetas_cache[indice] == bloque) { //hit de caché
            for (int j = 0; j < 4; j++) {
                ir[j] = cache[indice][palabra][j]; //copia al registro IR la intrucción codificada
            }
        } else { //fallo de caché
            int direccion_memoria = bloque * 16; //dirección en la que comienza el bloque que hay que cargar a caché
            int i = direccion_memoria;

            for (int j = 0; j < 4; j++) { //palabra
                for (int k = 0; k < 4; k++) { //byte
                    cache[indice][j][k] = memoria_principal[i + (j * 4) + k]; //copia a caché el bloque de la instrucción
                }
            }

            for (int j = 0; j < 4; j++) {
                ir[j] = cache[indice][palabra][j]; //copia al registro IR la intrucción codificada
            }

            etiquetas_cache[indice] = bloque;

            fallo_cache();
        }
    }

    public void fallo_cache() { //le dice al hilo padre que ya terminó su ejecución (por 16 ciclos)
        for (int i = 0; i < 16; i++) {
            try {
                barrera.await();
            } catch (InterruptedException | BrokenBarrierException ex) {
                Logger.getLogger(CPU.class.getName()).log(Level.SEVERE, null, ex);
            }

            reloj[hilo_actual]++; //aumenta la cantidad de ciclos que tardó la ejecución del hilo actual

            try {
                barrera.await();
            } catch (InterruptedException | BrokenBarrierException ex) {
                Logger.getLogger(CPU.class.getName()).log(Level.SEVERE, null, ex);
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
//                System.out.println("¡Codigo de operacion invalido!");
                break;
        }
        reloj[hilo_actual]++; //aumenta la cantidad de ciclos que tardó la ejecución del hilo actual
        quantum--; //el quantum se resta solo cuando se ejecuta una instrucción
    }

    public void cambio_contexto() { //cambia de contexto al hilo actual por el siguiente en la cola (actual+1)
        int siguiente_hilo = (hilo_actual + 1) % cant_hilos;

        if (!procesamiento_terminado()) {
            while (hilos_terminados[siguiente_hilo] == true) {
                siguiente_hilo = (siguiente_hilo + 1) % cant_hilos; //siguiente hilo que no ha terminado ejecución
            }

            contexto[hilo_actual][32] = pc;
            for (int i = 0; i < 32; i++) {
                contexto[hilo_actual][i] = registros[i]; //guarda los registros de hilo actual
            }

            pc = contexto[siguiente_hilo][32];
            for (int i = 0; i < 32; i++) {
                registros[i] = contexto[siguiente_hilo][i]; //carga los registros del siguiente hilo
            }

            hilo_actual = siguiente_hilo;
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
        hilos_terminados[hilo_actual] = true; //final de un hilo MIPS
    }

    public String imprimir_resultados() { //devuelve un String con los resultados finales del CPU
        String registros_String = ("- - Registros de CPU " + id + " - -");
        String espacio;
        for (int j = 0; j < cant_hilos; j++) {
            registros_String += ("\n\n- - Hilo " + hilos.get(j).getName() + " - -\n");
            for (int i = 0; i < 32; i++) {
                if (i < 9) {
                    espacio = "    ";
                } else {
                    espacio = "  ";
                }
                registros_String += ("R" + i + espacio + contexto[j][i] + "\n");
            }
            registros_String += "\nTardó en ejecutarse " + reloj[j] + " ciclos.";
        }

        return registros_String;
    }

    public boolean procesamiento_terminado() { //determina si la CPU terminó de ejecutar todos sus hilos
        int j = 0;
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

    private void cargar_hilos_memoria(){ //carga los hilos MIPS a la memoria del CPU
        int inicioMemoria = 128;

        for (int i = 0; i < hilos.size(); i++){
            contexto[i][32] = inicioMemoria;
            Path filePath = hilos.get(i).toPath() ;
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
        while (true) {
            try {
                barrera.await();
            } catch (InterruptedException | BrokenBarrierException ex) {
                Logger.getLogger(CPU.class.getName()).log(Level.SEVERE, null, ex);
            }

            if (!procesamiento_terminado()) {
                if ((quantum > 0) && (hilos_terminados[hilo_actual] == false)) {
                    cargar_instruccion(pc);
                    ejecutar_instruccion();
                }
                quantum = quantum_original;
                cambio_contexto();
            }

            try {
                barrera.await();
            } catch (InterruptedException | BrokenBarrierException ex) {
                Logger.getLogger(CPU.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
