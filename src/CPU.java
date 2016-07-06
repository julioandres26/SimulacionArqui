import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.Lock;
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
    public int cache_de_instrucciones[][][] = new int[4][4][4]; //índice, parabra, byte.

    int caches_de_datos[][][];
    int memorias_compartidas[][];
    int directorios[][][];
    int registrosRL[];
    Lock candados_directorios[];
    Lock candados_caches[];

    public int bloque_candado_LL;
    public boolean bandera_LL;

    public CPU(int id, int quantum, List<File> hilos, CyclicBarrier barrera, int caches_de_datos[][][], int memorias_compartidas[][], int directorios[][][], Lock candados_caches[], Lock candados_directorios[], int registrosRL[]) {
        this.id = id;
        this.quantum = quantum;
        this.quantum_original = quantum;
        this.barrera = barrera;
        this.hilos = hilos;

        this.caches_de_datos = caches_de_datos;
        this.memorias_compartidas = memorias_compartidas;
        this.directorios = directorios;
        this.registrosRL = registrosRL;
        this.candados_caches = candados_caches;
        this.candados_directorios = candados_directorios;

        pc = 128; //inicio de la primera instrucción
        pc_contexto = 0;
        cant_hilos = hilos.size();
        hilo_actual = 0;
        bloque_candado_LL = -1;

        contexto = new int[cant_hilos][33];
        hilos_terminados = new boolean[cant_hilos];
        reloj = new int[cant_hilos];

        terminado = false;
        bandera_LL = false;

        for (int i = 0; i < cant_hilos; i++) {
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
                    cache_de_instrucciones[i][j][k] = 0;
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
                ir[j] = cache_de_instrucciones[indice][palabra][j]; //copia al registro IR la intrucción codificada
            }
        } else { //fallo de caché
            int direccion_memoria = bloque * 16; //dirección en la que comienza el bloque que hay que cargar a caché
            int i = direccion_memoria;

            for (int j = 0; j < 4; j++) { //palabra
                for (int k = 0; k < 4; k++) { //byte
                    cache_de_instrucciones[indice][j][k] = memoria_principal[i + (j * 4) + k]; //copia a caché el bloque de la instrucción
                }
            }

            for (int j = 0; j < 4; j++) {
                ir[j] = cache_de_instrucciones[indice][palabra][j]; //copia al registro IR la intrucción codificada
            }

            etiquetas_cache[indice] = bloque;
        
            ciclos_de_retraso(16); //fallo de caché
        }
    }

    public void ciclos_de_retraso(int n) { //le dice al hilo padre que ya terminó su ejecución (por n ciclos)
        for (int i = 0; i < n; i++) {
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
            case 35:
                LW(ir[1], ir[2], ir[3]);
                break;
            case 43:
                SW(ir[1], ir[2], ir[3]);
                break;
            case 50:
                LL(ir[1], ir[2], ir[3]);
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

    public void tire_barreras(int i){
        for (int j = 1; j <= i; j++){
            try {
                barrera.await();
            } catch (InterruptedException | BrokenBarrierException ex) {
                Logger.getLogger(CPU.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void cambio_contexto() { //cambia de contexto al hilo actual por el siguiente en la cola (actual+1)
        int siguiente_hilo = (hilo_actual + 1) % cant_hilos;
        // ATENCION NO OLVIDAR al cambiar contexto tambien actualizar el RL en el array de RLs
        // DEBE ser inicializado al cargar un contexto en -1 el RL.

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
    
    public void LL(int RY, int RX, int n) { //RX <- M[n+RY], RL <- n+RY
        LW(RY, RX, n); // Dentro del LW se verifica si es un LL y se hace RL <- n+RY, se activa bandera_LL y se actualiza bloque_candado_LL.
    }

    public void LW(int RY, int RX, int n) {
        //ETIQUETAS CACHE: C = 0, M = 1, I = 2
        //ETIQUETAS DIRECTORIO: C = 0, M = 1, U = 2
        
        pc += 4;
        int direccion_de_memoria = n + registros[RY]; //Calcular la direccion de memoria, al sumar el inmediato con el valor del registro RY.
        int bloque = direccion_de_memoria / 16; //Numero de bloque donde está la direccion de memoria.
        int memoria_compartida_CPU = bloque / 8; //# de memoria compartida (cual CPU) está el bloque.
        int indice = bloque % 4; //índice de la caché para el bloque actual (mapeo directo).
        int palabra = (direccion_de_memoria % 16) / 4; //palabra que se va a leer
        int indice_de_directorio_de_bloque_a_leer = bloque % 8;

        if (candados_caches[id].tryLock()) { //tryLock en mi caché
            try {
                if ((caches_de_datos[id][indice][4] == bloque) && (caches_de_datos[id][indice][5] != 2)) {
                    //SÍ ESTÁ EN MI CACHÉ (C Ó M) -> LEER
                    registros[RX] = caches_de_datos[id][indice][palabra];

                    if (ir[0] == 50) { //ES UN LL
                        //- Activar una bandera para saber que hay un LL activo. (La bandera se desactiva en el SC???)
                        bandera_LL = true;
                        //- Copiar en una variable el número de bloque del que se leyó el candado.
                        bloque_candado_LL = (n + registros[RY]) / 16; //Número de bloque donde está la direccion de memoria de la que se leyó el candado.
                        registrosRL[id] = n + registros[RY];
                    }
                } else {
                    //NO ESTÁ EN MI CACHÉ -> ENTRAMOS A LIDIAR CON LA VICTIMA
                    if (caches_de_datos[id][indice][5] != 2) { //La etiqueta de la victima es diferente de I? (La víctima está C ó M en otra caché)
                        int bloque_victima = caches_de_datos[id][indice][4]; //Busca la etiqueta del bloque victima
                        int indice_de_directorio_de_bloque_victima = bloque_victima % 8;
                        int directorio_o_memoria_compartida_de_victima = bloque_victima / 8;

                        if (candados_directorios[directorio_o_memoria_compartida_de_victima].tryLock()) { //pide directorio de la víctima
                            try {
                                if (caches_de_datos[id][indice][5] == 0) { //Pregunto si la etiqueta del bloque victima es C.
                                    // VICTIMA ESTA COMPARTIDA
                                    directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][id] = 0; // quito el C de la casilla de este procesador
                                    int cont = 0;
                                    for (int i = 0; i < 3; i++) { // cuento cuantos procesadores aun la tienen compartida
                                        if (directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][i] == 1) {
                                            cont++;
                                        }
                                    }
                                    if (cont == 0) { // si ningun procesador la tiene compartida, pongo U
                                        directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][3] = 2; //cambia la etiqueta a U
                                    }
                                } else {
                                    // VÍCTIMA ESTÁ MODIFICADA
                                    // Vamos a actulizar directorios
                                    directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][id] = 0; //Se modifica para el procesador actual y se pone en 0.
                                    directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][3] = 2; //Se modifica la etiqueta y se pone en U.
                                    for (int i = 0; i < 4; i++) { // MOVEMOS VICTIMA A MEMORIA
                                        memorias_compartidas[directorio_o_memoria_compartida_de_victima][((bloque_victima % 8) * 4) + i] = caches_de_datos[id][indice][i];
                                    }
                                    caches_de_datos[id][indice][5] = 2; //Pongo el estado del bloque en I.
                                }
                            } finally {
                                candados_directorios[directorio_o_memoria_compartida_de_victima].unlock();
                                // YA SE ELIMINÓ LA VÍCTIMA
                                if (candados_directorios[memoria_compartida_CPU].tryLock()) { //tryLock directorio del bloque a cargar
                                    subir_bloque_a_cache_y_leer(n, RY, RX, indice, bloque, palabra, memoria_compartida_CPU, indice_de_directorio_de_bloque_a_leer);
                                } else {
                                    pc = pc - 4;
                                    System.out.println("Desde el CPU " + id + " no pude entrar al directorio del bloque a leer!!!");
                                }
                            }
                        } else {
                            pc = pc - 4;
                            System.out.println("Desde el CPU " + id + " no pude entrar al directorio de la víctima!!!");
                        }
                    } else { // NO hay víctima -> La etiqueta está I
                        // NO HAY VICTIMA, porque está en I el estado de la supuesta victima
                        if (candados_directorios[memoria_compartida_CPU].tryLock()) { //tryLock directorio del bloque a cargar
                            subir_bloque_a_cache_y_leer(n, RY, RX, indice, bloque, palabra, memoria_compartida_CPU, indice_de_directorio_de_bloque_a_leer);
                        } else {
                            pc = pc - 4;
                            System.out.println("Desde el CPU " + id + " no pude entrar al directorio del bloque a leer!!!");
                        }
                    }
                } // FIN "NO ESTÁ EN MI CACHÉ"
            } finally {
                candados_caches[id].unlock();
            }
        } else { //no pudo adquirir el lock de mi cache
            pc = pc - 4;
            System.out.println("Desde el CPU " + id + " no pude entrar a mi cache de datos!!!");
        }
    }
    
    public void subir_bloque_a_cache_y_leer(int n, int RY, int RX, int indice, int bloque, int palabra, int memoria_compartida_CPU, int indice_de_directorio_de_bloque_a_leer) {
        try {
            if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_leer][3] == 1) { // Reviso si el bloque a cargar está modificado en otro CPU
                // Como está M en otro CPU, debo bajarlo a memoria primero
                int CPU_que_tiene_bloque_modificado; // Esta valor es por defecto, para que compile.
                if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_leer][(id + 1) % 3] == 1) {
                    CPU_que_tiene_bloque_modificado = (id + 1) % 3; //la primera siguiente caché que no es mi caché
                } else {
                    CPU_que_tiene_bloque_modificado = (id + 2) % 3; //la segunda siguiente caché que no es mi caché
                }
                if (candados_caches[CPU_que_tiene_bloque_modificado].tryLock()) {
                    try {
                        for (int i = 0; i < 4; i++) { // MOVEMOS VICTIMA que si es el bloque que queremos leer A MEMORIA y de una vez lo subimos a nuestra caché
                            memorias_compartidas[memoria_compartida_CPU][((bloque % 8) * 4) + i] = caches_de_datos[CPU_que_tiene_bloque_modificado][indice][i];
                            caches_de_datos[id][indice][i] = caches_de_datos[CPU_que_tiene_bloque_modificado][indice][i];
                        }
                        caches_de_datos[id][indice][4] = bloque; //Se actualiza la etiqueta
                        caches_de_datos[id][indice][5] = 0; //Se pone C en el estado en mi caché
                        caches_de_datos[CPU_que_tiene_bloque_modificado][indice][5] = 0; //Se pone C en el estado en la caché remota
                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_leer][id] = 1;
                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_leer][3] = 0;

                        // LEER porque ya subimos bloque a nuestra caché entonces estamos como el primer caso
                        registros[RX] = caches_de_datos[id][indice][palabra];
                        if (ir[0] == 50) { //ES UN LL
                            //- Activar una bandera para saber que hay un LL activo. (La bandera se desactiva en el SC???)
                            bandera_LL = true;
                            //- Copiar en una variable el número de bloque del que se leyó el candado.
                            bloque_candado_LL = (n + registros[RY]) / 16; //Número de bloque donde está la direccion de memoria de la que se leyó el candado.
                            registrosRL[id] = n + registros[RY];
                        }
                    } finally {
                        candados_caches[CPU_que_tiene_bloque_modificado].unlock();
                    }
                } else {
                    pc = pc - 4;
                    System.out.println("Desde el CPU " + id + " no pude entrar a la caché donde el bloque está M!!!");
                }
            } else { // SI NO ESTA M, quiere decir que está compartido o libre (U)
                int direccion_fisica;
                for (int i = 0; i < 4; i++) {
                    direccion_fisica = ((bloque % 8) * 4) + i;
                    caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][direccion_fisica];
                }
                caches_de_datos[id][indice][4] = bloque; //Se actualiza la etiqueta
                caches_de_datos[id][indice][5] = 0; //Se pone C en el estado
                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_leer][id] = 1;
                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_leer][3] = 0;

                //LEER
                registros[RX] = caches_de_datos[id][indice][palabra];

                if (ir[0] == 50) { //ES UN LL
                    //- Activar una bandera para saber que hay un LL activo. (La bandera se desactiva en el SC???)
                    bandera_LL = true;
                    //- Copiar en una variable el número de bloque del que se leyó el candado.
                    bloque_candado_LL = (n + registros[RY]) / 16; //Número de bloque donde está la direccion de memoria de la que se leyó el candado.
                    registrosRL[id] = n + registros[RY];
                }
            }
        } finally {
            candados_directorios[memoria_compartida_CPU].unlock();
        }
    }

    public void SW(int RY, int RX, int n){
        //ETIQUETAS CACHE: C = 0, M = 1, I = 2
        //ETIQUETAS DIRECTORIO: C = 0, M = 1, U = 2
        pc += 4;

        int direccion_de_memoria = n + registros[RY]; //Calcular la direccion de memoria, al sumar el inmediato con el valor del registro RY.
        int bloque = direccion_de_memoria / 16; //Numero de bloque donde está la direccion de memoria.
        int memoria_compartida_CPU = bloque / 8; //# de memoria compartida (cual CPU) está el bloque.
        int indice = bloque % 4; //índice de la caché para el bloque a escribir (mapeo directo).
        int palabra = (direccion_de_memoria % 16) / 4; //palabra que se va a escribir
        int indice_de_directorio_de_bloque_a_escribir = bloque % 8;

        if (candados_caches[id].tryLock()){
            try {
                if (caches_de_datos[id][indice][4] == bloque && caches_de_datos[id][indice][5] != 2){
                    // si entro es que el bloque a escribir lo tengo
                    if (caches_de_datos[id][indice][5] == 1){
                        // BLOQUE ESTÁ M
                        caches_de_datos[id][indice][palabra] = registros[RX];
                    } else {
                        // BLOQUE ESTÁ C
                        if (candados_directorios[memoria_compartida_CPU].tryLock()){
                            try {
                                int  cont = 0;
                                for (int i = 0; i < 3; i++){
                                    if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][i] == 1){
                                        cont += 1;
                                    }
                                }
                                if (cont == 1){ // SOLO YO LO TENGO COMPARTIDO
                                    directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1;
                                    caches_de_datos[id][indice][5] = 1;
                                    caches_de_datos[id][indice][palabra] = registros[RX];
                                } else {
                                    if (cont == 2){ // OTRA CACHE ADEMAS DE LA MIA LO TIENE COMPARTIDO, DEBO INVALIDARLO EN ESA CACHE
                                        int cache_remota = 0; //# de la otra caché donde está compartido el bloque
                                        if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][(id + 1) % 3] == 1){
                                            cache_remota = (id + 1) % 3; //la primera siguiente caché que no es mi caché
                                        } else {
                                            cache_remota = (id + 2) % 3; //la segunda siguiente caché que no es mi caché
                                        }
                                        if (candados_caches[cache_remota].tryLock()){
                                            try {
                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota] = 0;
                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1;
                                                caches_de_datos[cache_remota][indice][5] = 2;
                                                caches_de_datos[id][indice][5] = 1;
                                                caches_de_datos[id][indice][palabra] = registros[RX];
                                            } finally {
                                                candados_caches[cache_remota].unlock();
                                            }
                                        } else {
                                            pc -= 4;
                                            System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota + "!!!");
                                        }
                                    } else {
                                        // ESTA EN LAS TRES CACHÉS DE DATOS Y COMPARTIDO
                                        int cache_remota1 = (id + 1) % 3;
                                        int cache_remota2 = (id + 2) % 3;
                                        if (candados_caches[cache_remota1].tryLock()){
                                            try {
                                                // INVALIDO LA CACHE 1
                                                caches_de_datos[cache_remota1][indice][5] = 2;
                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota1] = 0;
                                                if (candados_caches[cache_remota2].tryLock()){ // INTENTO INVALIDAR LA CACHE 2
                                                    try {
                                                        caches_de_datos[cache_remota2][indice][5] = 2;
                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota2] = 0;
                                                        caches_de_datos[id][indice][5] = 1;
                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1;
                                                        caches_de_datos[id][indice][palabra] = registros[RX];
                                                    } finally {
                                                        candados_caches[cache_remota2].unlock();
                                                    }
                                                } else {
                                                    pc -= 4;
                                                    System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota2 + "!!!");

                                                }
                                            } finally {
                                                candados_caches[cache_remota1].unlock();
                                            }
                                        } else {
                                            pc -= 4;
                                            System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota1 + "!!!");
                                        }
                                    }
                                }
                            } finally {
                                candados_directorios[memoria_compartida_CPU].unlock();
                            }
                        } else {
                            pc -= 4;
                            System.out.println("Desde el CPU " + id + " no pude entrar al directorio del bloque a escribir!!!");
                        }
                    }
                } else {
                    // si entro aca es que el bloque a escribir no lo tengo del todo
                    // pregunto por una VICTIMA
                    if (caches_de_datos[id][indice][5] == 2){
                        //LA VICTIMA ES INVALIDA, solo tengo que subir bloque nuevo
                        if (candados_directorios[memoria_compartida_CPU].tryLock()){
                            try {
                                if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] == 2) {
                                    // Caso mas sencillo: el bloque está libre / U
                                    directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][id] = 1;
                                    directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1;
                                    caches_de_datos[id][indice][5] = 1; // pongo M en cache
                                    caches_de_datos[id][indice][4] = bloque; // actualizo etiqueta
                                    // Subo bloque a caché
                                    for (int i = 0; i < 4; i++) {
                                        int direccion_fisica = ((bloque % 8) * 4) + i;
                                        caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][direccion_fisica];
                                    }
                                    caches_de_datos[id][indice][palabra] = registros[RX];
                                } else {
                                    if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] == 0){
                                        // Caso donde el bloque está compartido entonces solo debemos subirlo y actualizar directorio e invalidar caches
                                        int  cont = 0;
                                        for (int i = 0; i < 3; i++){
                                            if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][i] == 1){
                                                cont += 1;
                                            }
                                        }
                                        if (cont == 1){ // solo una cache lo tiene compartido
                                            int cache_remota = 0; //# de la otra caché donde está compartido el bloque
                                            if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][(id + 1) % 3] == 1){
                                                cache_remota = (id + 1) % 3; //la primera siguiente caché que no es mi caché
                                            } else {
                                                cache_remota = (id + 2) % 3; //la segunda siguiente caché que no es mi caché
                                            }
                                            if (candados_caches[cache_remota].tryLock()){
                                                try {
                                                    // invalido en esa otra cache, actualizo directorio, subo bloque a cache mia, escribo en cache mia
                                                    // pongo M en mi cache y en el directorio, actualizo etiqueta de bloque en mi cache
                                                    directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota] = 0;
                                                    directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1;
                                                    caches_de_datos[cache_remota][indice][5] = 2;
                                                    for (int i = 0; i < 4; i++) {
                                                        int direccion_fisica = ((bloque % 8) * 4) + i;
                                                        caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][direccion_fisica];
                                                    }
                                                    caches_de_datos[id][indice][4] = bloque;
                                                    caches_de_datos[id][indice][5] = 1;
                                                    caches_de_datos[id][indice][palabra] = registros[RX];
                                                } finally {
                                                    candados_caches[cache_remota].unlock();
                                                }
                                            } else {
                                                pc -= 4;
                                                System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota + "!!!");
                                            }
                                        } else {
                                            // esta compartido en dos otras caches, debo invalidarlas primero
                                            int cache_remota1 = (id + 1) % 3;
                                            int cache_remota2 = (id + 2) % 3;
                                            if (candados_caches[cache_remota1].tryLock()){
                                                try {
                                                    // INVALIDO LA CACHE 1
                                                    caches_de_datos[cache_remota1][indice][5] = 2;
                                                    directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota1] = 0;
                                                    if (candados_caches[cache_remota2].tryLock()){ // INTENTO INVALIDAR LA CACHE 2
                                                        try {
                                                            // si llegue hasta aca es que podre invalidar ambas caches y proceder con el SW
                                                            caches_de_datos[cache_remota2][indice][5] = 2;
                                                            directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota2] = 0;
                                                            for (int i = 0; i < 4; i++) {
                                                                int direccion_fisica = ((bloque % 8) * 4) + i;
                                                                caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][direccion_fisica];
                                                            }
                                                            caches_de_datos[id][indice][4] = bloque;
                                                            caches_de_datos[id][indice][5] = 1;
                                                            directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1;
                                                            caches_de_datos[id][indice][palabra] = registros[RX];
                                                        } finally {
                                                            candados_caches[cache_remota2].unlock();
                                                        }
                                                    } else {
                                                        pc -= 4;
                                                        System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota2 + "!!!");

                                                    }
                                                } finally {
                                                    candados_caches[cache_remota1].unlock();
                                                }
                                            } else {
                                                pc -= 4;
                                                System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota1 + "!!!");
                                            }
                                        }
                                    } else {
                                        // bloque esta MODIFICADO en otra cache, debemos bajarlo a memoria, subirlo a cache nuestra y actualizar directorios
                                        int  cache_remota_donde_esta_M = 0;
                                        for (int i = 0; i < 3; i++){
                                            if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][i] == 1){
                                                cache_remota_donde_esta_M = i;
                                            }
                                        }
                                        if (candados_caches[cache_remota_donde_esta_M].tryLock()){
                                            try {
                                                // bajamos a memoria y subimos a nuestra cache
                                                for (int i = 0; i < 4; i++) {
                                                    int direccion_fisica = ((bloque % 8) * 4) + i;
                                                    memorias_compartidas[memoria_compartida_CPU][direccion_fisica] = caches_de_datos[cache_remota_donde_esta_M][indice][i];
                                                    caches_de_datos[id][indice][i] = caches_de_datos[cache_remota_donde_esta_M][indice][i];
                                                }
                                                caches_de_datos[cache_remota_donde_esta_M][indice][5] = 2; // invalidamos cache remota
                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota_donde_esta_M] = 0;
                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][id] = 1;
                                                caches_de_datos[id][indice][4] = bloque;
                                                caches_de_datos[id][indice][5] = 1;
                                                caches_de_datos[id][indice][palabra] = registros[RX];
                                            } finally {
                                                candados_caches[cache_remota_donde_esta_M].unlock();
                                            }
                                        } else {
                                            pc -= 4;
                                            System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota_donde_esta_M + "!!!");
                                        }
                                    }
                                }
                            } finally {
                                candados_directorios[memoria_compartida_CPU].unlock();
                            }
                        } else {
                            pc -= 4;
                            System.out.println("Desde el CPU " + id + " no pude entrar al directorio del bloque a escribir!!!");
                        }
                    } else {
                        // LA VICTIMA ES COMPARTIDA O MODIFICADA
                        int bloque_victima = caches_de_datos[id][indice][4]; //Busca la etiqueta del bloque victima
                        int directorio_o_memoria_compartida_de_victima = bloque_victima / 8;
                        int indice_de_directorio_de_bloque_victima = bloque_victima % 8;

                        if (candados_directorios[directorio_o_memoria_compartida_de_victima].tryLock()) {
                            try {
                                // empezamos cuando la victima es compartida
                                if (caches_de_datos[id][indice][5] == 0) {
                                    int cont = 0;
                                    for (int i = 0; i < 3; i++) {
                                        if (directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][i] == 1) {
                                            cont += 1;
                                        }
                                    }
                                    if (cont == 1) {
                                        // solo yo tenia la victima compartida
                                        directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][3] = 2;
                                        directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][id] = 0;
                                    }
                                    directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][id] = 0;
                                    if (candados_directorios[memoria_compartida_CPU].tryLock()){
                                        try {
                                            if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] == 2) {
                                                // Caso mas sencillo: el bloque está libre / U
                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][id] = 1;
                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1;
                                                caches_de_datos[id][indice][5] = 1; // pongo M en cache
                                                caches_de_datos[id][indice][4] = bloque; // actualizo etiqueta
                                                // Subo bloque a caché
                                                for (int i = 0; i < 4; i++) {
                                                    int direccion_fisica = ((bloque % 8) * 4) + i;
                                                    caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][direccion_fisica];
                                                }
                                                caches_de_datos[id][indice][palabra] = registros[RX];
                                            } else {
                                                if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] == 0){
                                                    // Caso donde el bloque está compartido entonces solo debemos subirlo y actualizar directorio e invalidar caches
                                                    int  cont2 = 0;
                                                    for (int i = 0; i < 3; i++){
                                                        if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][i] == 1){
                                                            cont2 += 1;
                                                        }
                                                    }
                                                    if (cont2 == 1){ // solo una cache lo tiene compartido
                                                        int cache_remota = 0; //# de la otra caché donde está compartido el bloque
                                                        if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][(id + 1) % 3] == 1){
                                                            cache_remota = (id + 1) % 3; //la primera siguiente caché que no es mi caché
                                                        } else {
                                                            cache_remota = (id + 2) % 3; //la segunda siguiente caché que no es mi caché
                                                        }
                                                        if (candados_caches[cache_remota].tryLock()){
                                                            try {
                                                                // invalido en esa otra cache, actualizo directorio, subo bloque a cache mia, escribo en cache mia
                                                                // pongo M en mi cache y en el directorio, actualizo etiqueta de bloque en mi cache
                                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota] = 0;
                                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1;
                                                                caches_de_datos[cache_remota][indice][5] = 2;
                                                                for (int i = 0; i < 4; i++) {
                                                                    int direccion_fisica = ((bloque % 8) * 4) + i;
                                                                    caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][direccion_fisica];
                                                                }
                                                                caches_de_datos[id][indice][4] = bloque;
                                                                caches_de_datos[id][indice][5] = 1;
                                                                caches_de_datos[id][indice][palabra] = registros[RX];
                                                            } finally {
                                                                candados_caches[cache_remota].unlock();
                                                            }
                                                        } else {
                                                            pc -= 4;
                                                            System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota + "!!!");
                                                        }
                                                    } else {
                                                        // esta compartido en dos otras caches, debo invalidarlas primero
                                                        int cache_remota1 = (id + 1) % 3;
                                                        int cache_remota2 = (id + 2) % 3;
                                                        if (candados_caches[cache_remota1].tryLock()){
                                                            try {
                                                                // INVALIDO LA CACHE 1
                                                                caches_de_datos[cache_remota1][indice][5] = 2;
                                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota1] = 0;
                                                                if (candados_caches[cache_remota2].tryLock()){ // INTENTO INVALIDAR LA CACHE 2
                                                                    try {
                                                                        // si llegue hasta aca es que podre invalidar ambas caches y proceder con el SW
                                                                        caches_de_datos[cache_remota2][indice][5] = 2;
                                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota2] = 0;
                                                                        for (int i = 0; i < 4; i++) {
                                                                            int direccion_fisica = ((bloque % 8) * 4) + i;
                                                                            caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][direccion_fisica];
                                                                        }
                                                                        caches_de_datos[id][indice][4] = bloque;
                                                                        caches_de_datos[id][indice][5] = 1;
                                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1;
                                                                        caches_de_datos[id][indice][palabra] = registros[RX];
                                                                    } finally {
                                                                        candados_caches[cache_remota2].unlock();
                                                                    }
                                                                } else {
                                                                    pc -= 4;
                                                                    System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota2 + "!!!");

                                                                }
                                                            } finally {
                                                                candados_caches[cache_remota1].unlock();
                                                            }
                                                        } else {
                                                            pc -= 4;
                                                            System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota1 + "!!!");
                                                        }
                                                    }
                                                } else {
                                                    // bloque esta MODIFICADO en otra cache, debemos bajarlo a memoria, subirlo a cache nuestra y actualizar directorios
                                                    int  cache_remota_donde_esta_M = 0;
                                                    for (int i = 0; i < 3; i++){
                                                        if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][i] == 1){
                                                            cache_remota_donde_esta_M = i;
                                                        }
                                                    }
                                                    if (candados_caches[cache_remota_donde_esta_M].tryLock()){
                                                        try {
                                                            // bajamos a memoria y subimos a nuestra cache
                                                            for (int i = 0; i < 4; i++) {
                                                                int direccion_fisica = ((bloque % 8) * 4) + i;
                                                                memorias_compartidas[memoria_compartida_CPU][direccion_fisica] = caches_de_datos[cache_remota_donde_esta_M][indice][i];
                                                                caches_de_datos[id][indice][i] = caches_de_datos[cache_remota_donde_esta_M][indice][i];
                                                            }
                                                            caches_de_datos[cache_remota_donde_esta_M][indice][5] = 2; // invalidamos cache remota
                                                            directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota_donde_esta_M] = 0;
                                                            directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][id] = 1;
                                                            caches_de_datos[id][indice][4] = bloque;
                                                            caches_de_datos[id][indice][5] = 1;
                                                            caches_de_datos[id][indice][palabra] = registros[RX];
                                                        } finally {
                                                            candados_caches[cache_remota_donde_esta_M].unlock();
                                                        }
                                                    } else {
                                                        pc -= 4;
                                                        System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota_donde_esta_M + "!!!");
                                                    }
                                                }
                                            }
                                        } finally {
                                            candados_directorios[memoria_compartida_CPU].unlock();
                                        }
                                    } else {
                                        pc -= 4;
                                        System.out.println("Desde el CPU " + id + " no pude entrar al directorio del bloque a escribir!!!");
                                    }
                                } else {
                                    // la victima es modificada
                                    if (candados_directorios[directorio_o_memoria_compartida_de_victima].tryLock()){
                                        try {
                                            directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][id] = 0;
                                            directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][3] = 2;
                                            for (int i = 0; i < 4; i++) {
                                                int direccion_fisica = ((bloque % 8) * 4) + i;
                                                memorias_compartidas[directorio_o_memoria_compartida_de_victima][direccion_fisica] = caches_de_datos[id][indice][i];
                                            }
                                        } finally {
                                            candados_directorios[directorio_o_memoria_compartida_de_victima].unlock();
                                            if (candados_directorios[memoria_compartida_CPU].tryLock()){
                                                try {
                                                    if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] == 2) {
                                                        // Caso mas sencillo: el bloque está libre / U
                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][id] = 1;
                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1;
                                                        caches_de_datos[id][indice][5] = 1; // pongo M en cache
                                                        caches_de_datos[id][indice][4] = bloque; // actualizo etiqueta
                                                        // Subo bloque a caché
                                                        for (int i = 0; i < 4; i++) {
                                                            int direccion_fisica = ((bloque % 8) * 4) + i;
                                                            caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][direccion_fisica];
                                                        }
                                                        caches_de_datos[id][indice][palabra] = registros[RX];
                                                    } else {
                                                        if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] == 0){
                                                            // Caso donde el bloque está compartido entonces solo debemos subirlo y actualizar directorio e invalidar caches
                                                            int  cont = 0;
                                                            for (int i = 0; i < 3; i++){
                                                                if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][i] == 1){
                                                                    cont += 1;
                                                                }
                                                            }
                                                            if (cont == 1){ // solo una cache lo tiene compartido
                                                                int cache_remota = 0; //# de la otra caché donde está compartido el bloque
                                                                if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][(id + 1) % 3] == 1){
                                                                    cache_remota = (id + 1) % 3; //la primera siguiente caché que no es mi caché
                                                                } else {
                                                                    cache_remota = (id + 2) % 3; //la segunda siguiente caché que no es mi caché
                                                                }
                                                                if (candados_caches[cache_remota].tryLock()){
                                                                    try {
                                                                        // invalido en esa otra cache, actualizo directorio, subo bloque a cache mia, escribo en cache mia
                                                                        // pongo M en mi cache y en el directorio, actualizo etiqueta de bloque en mi cache
                                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota] = 0;
                                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1;
                                                                        caches_de_datos[cache_remota][indice][5] = 2;
                                                                        for (int i = 0; i < 4; i++) {
                                                                            int direccion_fisica = ((bloque % 8) * 4) + i;
                                                                            caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][direccion_fisica];
                                                                        }
                                                                        caches_de_datos[id][indice][4] = bloque;
                                                                        caches_de_datos[id][indice][5] = 1;
                                                                        caches_de_datos[id][indice][palabra] = registros[RX];
                                                                    } finally {
                                                                        candados_caches[cache_remota].unlock();
                                                                    }
                                                                } else {
                                                                    pc -= 4;
                                                                    System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota + "!!!");
                                                                }
                                                            } else {
                                                                // esta compartido en dos otras caches, debo invalidarlas primero
                                                                int cache_remota1 = (id + 1) % 3;
                                                                int cache_remota2 = (id + 2) % 3;
                                                                if (candados_caches[cache_remota1].tryLock()){
                                                                    try {
                                                                        // INVALIDO LA CACHE 1
                                                                        caches_de_datos[cache_remota1][indice][5] = 2;
                                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota1] = 0;
                                                                        if (candados_caches[cache_remota2].tryLock()){ // INTENTO INVALIDAR LA CACHE 2
                                                                            try {
                                                                                // si llegue hasta aca es que podre invalidar ambas caches y proceder con el SW
                                                                                caches_de_datos[cache_remota2][indice][5] = 2;
                                                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota2] = 0;
                                                                                for (int i = 0; i < 4; i++) {
                                                                                    int direccion_fisica = ((bloque % 8) * 4) + i;
                                                                                    caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][direccion_fisica];
                                                                                }
                                                                                caches_de_datos[id][indice][4] = bloque;
                                                                                caches_de_datos[id][indice][5] = 1;
                                                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1;
                                                                                caches_de_datos[id][indice][palabra] = registros[RX];
                                                                            } finally {
                                                                                candados_caches[cache_remota2].unlock();
                                                                            }
                                                                        } else {
                                                                            pc -= 4;
                                                                            System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota2 + "!!!");

                                                                        }
                                                                    } finally {
                                                                        candados_caches[cache_remota1].unlock();
                                                                    }
                                                                } else {
                                                                    pc -= 4;
                                                                    System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota1 + "!!!");
                                                                }
                                                            }
                                                        } else {
                                                            // bloque esta MODIFICADO en otra cache, debemos bajarlo a memoria, subirlo a cache nuestra y actualizar directorios
                                                            int  cache_remota_donde_esta_M = 0;
                                                            for (int i = 0; i < 3; i++){
                                                                if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][i] == 1){
                                                                    cache_remota_donde_esta_M = i;
                                                                }
                                                            }
                                                            if (candados_caches[cache_remota_donde_esta_M].tryLock()){
                                                                try {
                                                                    // bajamos a memoria y subimos a nuestra cache
                                                                    for (int i = 0; i < 4; i++) {
                                                                        int direccion_fisica = ((bloque % 8) * 4) + i;
                                                                        memorias_compartidas[memoria_compartida_CPU][direccion_fisica] = caches_de_datos[cache_remota_donde_esta_M][indice][i];
                                                                        caches_de_datos[id][indice][i] = caches_de_datos[cache_remota_donde_esta_M][indice][i];
                                                                    }
                                                                    caches_de_datos[cache_remota_donde_esta_M][indice][5] = 2; // invalidamos cache remota
                                                                    directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota_donde_esta_M] = 0;
                                                                    directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][id] = 1;
                                                                    caches_de_datos[id][indice][4] = bloque;
                                                                    caches_de_datos[id][indice][5] = 1;
                                                                    caches_de_datos[id][indice][palabra] = registros[RX];
                                                                } finally {
                                                                    candados_caches[cache_remota_donde_esta_M].unlock();
                                                                }
                                                            } else {
                                                                pc -= 4;
                                                                System.out.println("Desde el CPU " + id + " no pude entrar a a la cache remota de " + cache_remota_donde_esta_M + "!!!");
                                                            }
                                                        }
                                                    }
                                                } finally {
                                                    candados_directorios[memoria_compartida_CPU].unlock();
                                                }
                                            } else {
                                                pc -= 4;
                                                System.out.println("Desde el CPU " + id + " no pude entrar al directorio del bloque a escribir!!!");
                                            }
                                        }
                                    } else {
                                        pc -= 4;
                                        System.out.println("Desde el CPU " + id + " no pude entrar al directorio del bloque victima!!!");
                                    }
                                }
                            } finally {
                                candados_directorios[directorio_o_memoria_compartida_de_victima].unlock();
                            }
                        } else {
                            pc -= 4;
                            System.out.println("Desde el CPU " + id + " no pude entrar al directorio de la victima!!!");
                        }
                    }
                }
            } finally {
                candados_caches[id].unlock();
            }
        } else {
            pc -= 4;
            System.out.println("Desde el CPU " + id + " no pude entrar a mi cache de datos!!!");
        }
    }

    /*public void SW(int RY, int RX, int n) {
        //ETIQUETAS CACHE: C = 0, M = 1, I = 2
        //ETIQUETAS DIRECTORIO: C = 0, M = 1, U = 2

        pc += 4;
        int direccion_de_memoria = n + registros[RY]; //Calcular la direccion de memoria, al sumar el inmediato con el valor del registro RY.
        int bloque = direccion_de_memoria / 16; //Numero de bloque donde está la direccion de memoria.
        int memoria_compartida_CPU = bloque / 8; //# de memoria compartida (cual CPU) está el bloque.
        int indice = bloque % 4; //índice de la caché para el bloque a escribir (mapeo directo).
        int palabra = (direccion_de_memoria % 16) / 4; //palabra que se va a escribir
        int indice_de_directorio_de_bloque_a_escribir = bloque % 8;

        // cache_de_datos[3][4][6] -> [# de cache][# de indice][0->3 = palabras, 4 = etiqueta, 5 = estado]
        // directorios[3][8][4] -> [# de directorio][# de bloque][0->2 = # de procesador, 3 = etiqueta]
        if (candados_caches[id].tryLock()) { //tryLock en mi caché
            try {
                if ((caches_de_datos[id][indice][4] == bloque) && (caches_de_datos[id][indice][5] != 2)) { //SÍ ESTÁ EN MI CACHÉ (C Ó M)
                    if ((caches_de_datos[id][indice][5] == 1)) { // ESTÁ EN MI CACHÉ M -> ESCRIBIR A CACHÉ
                        caches_de_datos[id][indice][palabra] = registros[RX];
                    } else { // ESTÁ EN MI CACHÉ C -> REVISAR SI ESTÁ EN OTRA CACHÉ Y ACTUALIZAR EL DIRECTORIO DEL BLOQUE A ESCRIBIR
                        if (candados_directorios[memoria_compartida_CPU].tryLock()) { //tryLock directorio del bloque
                            try {
                                //revisar si está compartido en alguna otra caché
                                int cont = 0;
                                for (int i = 0; i < 3; i++) {
                                    if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][i] == 1) {
                                        cont += 1;
                                    }
                                }
                                if (cont == 1) { //sólo está en mi caché
                                    //escribo y actualizo la caché y el directorio del bloque a escribir (de C a M)
                                    caches_de_datos[id][indice][palabra] = registros[RX];
                                    caches_de_datos[id][indice][5] = 1;
                                    directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1;
                                } else if (cont == 2) { //está en dos cachés (mi caché y otra)
                                    int cache_remota = 0; //# de la otra caché donde está compartido el bloque
                                    if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][(id + 1) % 3] == 1){
                                        cache_remota = (id + 1) % 3; //la primera siguiente caché que no es mi caché
                                    } else {
                                        cache_remota = (id + 2) % 3; //la segunda siguiente caché que no es mi caché
                                    }
                                    if (candados_caches[cache_remota].tryLock()) {
                                        try {
                                            //escribo y actualizo mi caché (de C a M)
                                            caches_de_datos[id][indice][palabra] = registros[RX];
                                            caches_de_datos[id][indice][5] = 1;
                                            //invalido el bloque en la caché remota y actualizo el directorio
                                            caches_de_datos[cache_remota][indice][5] = 2; //I
                                            directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota] = 0;
                                            directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1; //M
                                        } finally {
                                            candados_caches[cache_remota].unlock();
                                        }
                                    } else {
                                        pc = pc - 4;
                                        System.out.println("Desde el CPU " + id + " no pude entrar a la cache de datos de " + cache_remota + "!!!");
                                    }
                                } else { //está compartido en las tres cachés
                                    // Se trata de bloquear las dos cachés,
                                    // si me las dan las actualizo y las libero
                                    // si no me las dan se libera todo y se pone el pc -= 4.
                                    int cache_1 = (id + 1) % 3; //la primera siguiente caché que no es mi caché
                                    int cache_2 = (id + 2) % 3; //la segunda siguiente caché que no es mi caché
                                    if (candados_caches[cache_1].tryLock() && candados_caches[cache_2].tryLock()) {
                                        //se pudieron bloquear las dos cachés en el mismo ciclo
                                        try {
                                            //escribo y actualizo mi caché (de C a M)
                                            caches_de_datos[id][indice][palabra] = registros[RX];
                                            caches_de_datos[id][indice][5] = 1;
                                            //invalido el bloque en las cachés remotas y actualizo el directorio
                                            caches_de_datos[cache_1][indice][5] = 2; //I
                                            caches_de_datos[cache_2][indice][5] = 2; //I
                                            directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_1] = 0;
                                            directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_2] = 0;
                                            directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1; //M
                                        } finally {
                                            candados_caches[cache_1].unlock();
                                            candados_caches[cache_2].unlock();
                                        }
                                    } else {
                                        pc = pc - 4;
                                        System.out.println("Desde el CPU " + id + " no pude entrar a las caches de datos de " + cache_1 + " y " + cache_2 + "!!!");
                                    }
                                }
                            } finally {
                                candados_directorios[memoria_compartida_CPU].unlock();
                            }
                        } else {
                            pc = pc - 4;
                            System.out.println("Desde el CPU " + id + " no pude entrar al directorio del bloque a escribir!!!");
                        }
                    }
                } else { // NO ESTÁ EN MÍ CACHÉ
                    // NO ESTÁ EN MI CACHÉ -> ENTRAMOS A LIDIAR CON LA VICTIMA
                    if (caches_de_datos[id][indice][5] != 2) { //La etiqueta de la victima es diferente de I?
                        
                        
//                        guardar_victima_a_memoria(indice); //Buscar bloque víctima, copiarlo a memoria principal y actualizar caché(s) y directorio.
                        int bloque_victima = caches_de_datos[id][indice][4]; //Busca la etiqueta del bloque victima
                        int indice_de_directorio_de_bloque_victima = bloque_victima % 8;
                        int directorio_o_memoria_compartida_de_victima = bloque_victima / 8;
                        if (candados_directorios[directorio_o_memoria_compartida_de_victima].tryLock()) {
                            try {
                                if (caches_de_datos[id][indice][5] == 0) { //Pregunto si la etiqueta del bloque victima es C.
                                    // VICTIMA ESTA COMPARTIDA
                                    directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][id] = 0; // quito el C de la casilla de este procesador
                                    int cont = 0;
                                    for (int i = 0; i < 3; i++) { // cuento cuantos procesadores aun la tienen compartida
                                        if (directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][i] == 1) {
                                            cont++;
                                        }
                                    }
                                    if (cont == 0) { // si ningun procesador la tiene compartida, pongo U
                                        directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][3] = 2; //cambia la etiqueta a U
                                    }
                                } else {
                                    // VÍCTIMA ESTÁ MODIFICADA
                                    // Vamos a actulizar directorios
                                    directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][id] = 0; //Se modifica para el procesador actual y se pone en 0.
                                    directorios[directorio_o_memoria_compartida_de_victima][indice_de_directorio_de_bloque_victima][3] = 2; //Se modifica la etiqueta y se pone en U.
                                    for (int i = 0; i < 4; i++) { // MOVEMOS VICTIMA A MEMORIA
                                        memorias_compartidas[directorio_o_memoria_compartida_de_victima][((bloque_victima % 8) * 4) + i] = caches_de_datos[id][indice][i];
                                    }
                                    caches_de_datos[id][indice][5] = 2; //Pongo el estado del bloque en I.
                                }
                            } finally {
                                candados_directorios[directorio_o_memoria_compartida_de_victima].unlock();
                                
                                
                                


                                // NO HAY VICTIMA, porque está en I el estado de la supuesta victima
                                if (candados_directorios[memoria_compartida_CPU].tryLock()) { //tryLock directorio del bloque a cargar
                                    try {
                                        // Reviso si el bloque a cargar está modificado o compartido en otra CPU
                                        if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] == 1) {
                                            // BLOQUE ESTÁ M EN OTRA CPU -> BAJARLO A MEMORIA Y DESPUÉS COPIAR A MI CACHÉ
                                            int CPU_que_tiene_bloque_modificado; // Esta valor es por defecto, para que compile.
                                            if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][(id + 1) % 3] == 1) {
                                                CPU_que_tiene_bloque_modificado = (id + 1) % 3; //la primera siguiente caché que no es mi caché
                                            } else {
                                                CPU_que_tiene_bloque_modificado = (id + 2) % 3; //la segunda siguiente caché que no es mi caché
                                            }
                                            if (candados_caches[CPU_que_tiene_bloque_modificado].tryLock()) {
                                                try {
                                                    for (int i = 0; i < 4; i++) { // Copiar bloque a memoria y a mi caché
                                                        memorias_compartidas[memoria_compartida_CPU][((bloque % 8) * 4) + i] = caches_de_datos[CPU_que_tiene_bloque_modificado][indice][i];
                                                        caches_de_datos[id][indice][i] = caches_de_datos[CPU_que_tiene_bloque_modificado][indice][i];
                                                    }
                                                    caches_de_datos[id][indice][4] = bloque; //Se actualiza la etiqueta en mi caché
                                                    caches_de_datos[id][indice][5] = 1; //Se pone M en el estado en mi caché
                                                    caches_de_datos[CPU_que_tiene_bloque_modificado][indice][5] = 2; //Se invalida en la caché remota
                                                    //actualizar directorio
                                                    directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][id] = 1;
                                                    directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][CPU_que_tiene_bloque_modificado] = 0;

                                                    // ESCRIBIR porque ya subimos bloque a nuestra caché
                                                    caches_de_datos[id][indice][palabra] = registros[RX];
                                                } finally {
                                                    candados_caches[CPU_que_tiene_bloque_modificado].unlock();
                                                }
                                            } else {
                                                pc = pc - 4;
                                                System.out.println("Desde el CPU " + id + " no pude entrar a la caché donde el bloque está M!!!");
                                            }
                                        } else {
                                            // BLOQUE ESTÁ C EN UNA O MÁS CPU, O ESTÁ U
                                            if (candados_directorios[memoria_compartida_CPU].tryLock()) { //tryLock directorio del bloque a cargar
                                                try {
                                                    //revisar si está compartido en alguna otra caché
                                                    int cont = 0;
                                                    for (int i = 0; i < 3; i++) {
                                                        if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][i] == 1) {
                                                            cont += 1;
                                                        }
                                                    }
                                                    if (cont == 0) { //no está en ninguna caché, está U
                                                        //subo el bloque a mi caché
                                                        for (int i = 0; i < 4; i++) { // MOVEMOS VICTIMA que si es el bloque que queremos leer A MEMORIA y de una vez lo subimos a nuestra caché
                                                            caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][((bloque % 8) * 4) + i];
                                                        }
                                                        //actualizo la caché y el directorio
                                                        caches_de_datos[id][indice][4] = bloque;
                                                        caches_de_datos[id][indice][5] = 1; //M
                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][id] = 1;
                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1; //M
                                                        // ESCRIBIR porque ya subimos bloque a nuestra caché entonces estamos como el primer caso
                                                        caches_de_datos[id][indice][palabra] = registros[RX];
                                                    } else if (cont == 1) { //sólo está en una caché
                                                        int cache_remota; //# de la otra caché donde está compartido el bloque
                                                        if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][(id + 1) % 3] == 1) {
                                                            cache_remota = (id + 1) % 3; //la primera siguiente caché que no es mi caché
                                                        } else {
                                                            cache_remota = (id + 2) % 3; //la segunda siguiente caché que no es mi caché
                                                        }
                                                        if (candados_caches[cache_remota].tryLock()) {
                                                            try {
                                                                //invalido el bloque en la caché remota y actualizo el directorio
                                                                caches_de_datos[cache_remota][indice][5] = 2; //I
                                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota] = 0;
                                                            } finally {
                                                                candados_caches[cache_remota].unlock();

                                                                //subo el bloque a mi caché
                                                                for (int i = 0; i < 4; i++) { // MOVEMOS VICTIMA que si es el bloque que queremos leer A MEMORIA y de una vez lo subimos a nuestra caché
                                                                    caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][((bloque % 8) * 4) + i];
                                                                }
                                                                //actualizo la caché y el directorio
                                                                caches_de_datos[id][indice][4] = bloque;
                                                                caches_de_datos[id][indice][5] = 1; //M
                                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][id] = 1;
                                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1; //M
                                                                // ESCRIBIR porque ya subimos bloque a nuestra caché entonces estamos como el primer caso
                                                                caches_de_datos[id][indice][palabra] = registros[RX];
                                                            }
                                                        } else {
                                                            pc = pc - 4;
                                                            System.out.println("Desde el CPU " + id + " no pude entrar a la cache de datos de " + cache_remota + "!!!");
                                                        }
                                                    } else { //está compartido en las otras dos cachés
                                                        // Se trata de bloquear las dos cachés,
                                                        // si me las dan las actualizo y las libero
                                                        // si no me las dan se libera todo y se pone el pc -= 4.

                                                        int cache_1 = (id + 1) % 3; //la primera siguiente caché que no es mi caché
                                                        int cache_2 = (id + 2) % 3; //la segunda siguiente caché que no es mi caché
                                                        if (candados_caches[cache_1].tryLock() && candados_caches[cache_2].tryLock()) {
                                                            //se pudieron bloquear las dos cachés en el mismo ciclo
                                                            try {
                                                                //invalido el bloque en las cachés remotas y actualizo el directorio
                                                                caches_de_datos[cache_1][indice][5] = 2; //I
                                                                caches_de_datos[cache_2][indice][5] = 2; //I
                                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_1] = 0;
                                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_2] = 0;
                                                            } finally {
                                                                candados_caches[cache_1].unlock();
                                                                candados_caches[cache_2].unlock();

                                                                //subo el bloque a mi caché
                                                                for (int i = 0; i < 4; i++) { // MOVEMOS VICTIMA que si es el bloque que queremos leer A MEMORIA y de una vez lo subimos a nuestra caché
                                                                    caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][((bloque % 8) * 4) + i];
                                                                }
                                                                //actualizo la caché y el directorio
                                                                caches_de_datos[id][indice][4] = bloque;
                                                                caches_de_datos[id][indice][5] = 1; //M
                                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][id] = 1;
                                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1; //M
                                                                // ESCRIBIR porque ya subimos bloque a nuestra caché entonces estamos como el primer caso
                                                                caches_de_datos[id][indice][palabra] = registros[RX];
                                                            }
                                                        } else {
                                                            pc = pc - 4;
                                                            System.out.println("Desde el CPU " + id + " no pude entrar a las caches de datos de " + cache_1 + " y " + cache_2 + "!!!");
                                                        }
                                                    }
                                                } finally {
                                                    candados_directorios[memoria_compartida_CPU].unlock();
                                                }
                                            } else {
                                                pc = pc - 4;
                                                System.out.println("Desde el CPU " + id + " no pude entrar al directorio del bloque a escribir!!!");
                                            }
                                        }
                                    } finally {
                                        candados_directorios[memoria_compartida_CPU].unlock();
                                    }
                                } else {
                                    pc = pc - 4;
                                    System.out.println("Desde el CPU " + id + " no pude entrar al directorio del bloque a escribir!!!");
                                }


                                
                            }
                        } else {
                            pc = pc - 4;
                            System.out.println("Desde el CPU " + id + " no pude entrar al directorio de la víctima!!!");
                        }


                        
                        
                    } else {
                        // NO HAY VICTIMA, porque está en I el estado de la supuesta victima
                        if (candados_directorios[memoria_compartida_CPU].tryLock()) { //tryLock directorio del bloque a cargar
                            try {
                                // Reviso si el bloque a cargar está modificado o compartido en otra CPU
                                if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] == 1) {
                                    // BLOQUE ESTÁ M EN OTRA CPU -> BAJARLO A MEMORIA Y DESPUÉS COPIAR A MI CACHÉ
                                    int CPU_que_tiene_bloque_modificado; // Esta valor es por defecto, para que compile.
                                    if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][(id + 1) % 3] == 1) {
                                        CPU_que_tiene_bloque_modificado = (id + 1) % 3; //la primera siguiente caché que no es mi caché
                                    } else {
                                        CPU_que_tiene_bloque_modificado = (id + 2) % 3; //la segunda siguiente caché que no es mi caché
                                    }
                                    if (candados_caches[CPU_que_tiene_bloque_modificado].tryLock()) {
                                        try {
                                            for (int i = 0; i < 4; i++) { // Copiar bloque a memoria y a mi caché
                                                memorias_compartidas[memoria_compartida_CPU][((bloque % 8) * 4) + i] = caches_de_datos[CPU_que_tiene_bloque_modificado][indice][i];
                                                caches_de_datos[id][indice][i] = caches_de_datos[CPU_que_tiene_bloque_modificado][indice][i];
                                            }
                                            caches_de_datos[id][indice][4] = bloque; //Se actualiza la etiqueta en mi caché
                                            caches_de_datos[id][indice][5] = 1; //Se pone M en el estado en mi caché
                                            caches_de_datos[CPU_que_tiene_bloque_modificado][indice][5] = 2; //Se invalida en la caché remota
                                            //actualizar directorio
                                            directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][id] = 1;
                                            directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][CPU_que_tiene_bloque_modificado] = 0;

                                            // ESCRIBIR porque ya subimos bloque a nuestra caché
                                            caches_de_datos[id][indice][palabra] = registros[RX];
                                        } finally {
                                            candados_caches[CPU_que_tiene_bloque_modificado].unlock();
                                        }
                                    } else {
                                        pc = pc - 4;
                                        System.out.println("Desde el CPU " + id + " no pude entrar a la caché donde el bloque está M!!!");
                                    }
                                } else {
                                    // BLOQUE ESTÁ C EN UNA O MÁS CPU, O ESTÁ U
                                    if (candados_directorios[memoria_compartida_CPU].tryLock()) { //tryLock directorio del bloque a cargar
                                        try {
                                            //revisar si está compartido en alguna otra caché
                                            int cont = 0;
                                            for (int i = 0; i < 3; i++) {
                                                if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][i] == 1) {
                                                    cont += 1;
                                                }
                                            }
                                            if (cont == 0) { //no está en ninguna caché, está U
                                                //subo el bloque a mi caché
                                                for (int i = 0; i < 4; i++) { // MOVEMOS VICTIMA que si es el bloque que queremos leer A MEMORIA y de una vez lo subimos a nuestra caché
                                                    caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][((bloque % 8) * 4) + i];
                                                }
                                                //actualizo la caché y el directorio
                                                caches_de_datos[id][indice][4] = bloque;
                                                caches_de_datos[id][indice][5] = 1; //M
                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][id] = 1;
                                                directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1; //M
                                                // ESCRIBIR porque ya subimos bloque a nuestra caché entonces estamos como el primer caso
                                                caches_de_datos[id][indice][palabra] = registros[RX];
                                            } else if (cont == 1) { //sólo está en una caché
                                                int cache_remota; //# de la otra caché donde está compartido el bloque
                                                if (directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][(id + 1) % 3] == 1) {
                                                    cache_remota = (id + 1) % 3; //la primera siguiente caché que no es mi caché
                                                } else {
                                                    cache_remota = (id + 2) % 3; //la segunda siguiente caché que no es mi caché
                                                }
                                                if (candados_caches[cache_remota].tryLock()) {
                                                    try {
                                                        //invalido el bloque en la caché remota y actualizo el directorio
                                                        caches_de_datos[cache_remota][indice][5] = 2; //I
                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_remota] = 0;
                                                    } finally {
                                                        candados_caches[cache_remota].unlock();

                                                        //subo el bloque a mi caché
                                                        for (int i = 0; i < 4; i++) { // MOVEMOS VICTIMA que si es el bloque que queremos leer A MEMORIA y de una vez lo subimos a nuestra caché
                                                            caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][((bloque % 8) * 4) + i];
                                                        }
                                                        //actualizo la caché y el directorio
                                                        caches_de_datos[id][indice][4] = bloque;
                                                        caches_de_datos[id][indice][5] = 1; //M
                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][id] = 1;
                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1; //M
                                                        // ESCRIBIR porque ya subimos bloque a nuestra caché entonces estamos como el primer caso
                                                        caches_de_datos[id][indice][palabra] = registros[RX];
                                                    }
                                                } else {
                                                    pc = pc - 4;
                                                    System.out.println("Desde el CPU " + id + " no pude entrar a la cache de datos de " + cache_remota + "!!!");
                                                }
                                            } else { //está compartido en las otras dos cachés
                                                // Se trata de bloquear las dos cachés,
                                                // si me las dan las actualizo y las libero
                                                // si no me las dan se libera todo y se pone el pc -= 4.

                                                int cache_1 = (id + 1) % 3; //la primera siguiente caché que no es mi caché
                                                int cache_2 = (id + 2) % 3; //la segunda siguiente caché que no es mi caché
                                                if (candados_caches[cache_1].tryLock() && candados_caches[cache_2].tryLock()) {
                                                    //se pudieron bloquear las dos cachés en el mismo ciclo
                                                    try {
                                                        //invalido el bloque en las cachés remotas y actualizo el directorio
                                                        caches_de_datos[cache_1][indice][5] = 2; //I
                                                        caches_de_datos[cache_2][indice][5] = 2; //I
                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_1] = 0;
                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][cache_2] = 0;
                                                    } finally {
                                                        candados_caches[cache_1].unlock();
                                                        candados_caches[cache_2].unlock();

                                                        //subo el bloque a mi caché
                                                        for (int i = 0; i < 4; i++) { // MOVEMOS VICTIMA que si es el bloque que queremos leer A MEMORIA y de una vez lo subimos a nuestra caché
                                                            caches_de_datos[id][indice][i] = memorias_compartidas[memoria_compartida_CPU][((bloque % 8) * 4) + i];
                                                        }
                                                        //actualizo la caché y el directorio
                                                        caches_de_datos[id][indice][4] = bloque;
                                                        caches_de_datos[id][indice][5] = 1; //M
                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][id] = 1;
                                                        directorios[memoria_compartida_CPU][indice_de_directorio_de_bloque_a_escribir][3] = 1; //M
                                                        // ESCRIBIR porque ya subimos bloque a nuestra caché entonces estamos como el primer caso
                                                        caches_de_datos[id][indice][palabra] = registros[RX];
                                                    }
                                                } else {
                                                    pc = pc - 4;
                                                    System.out.println("Desde el CPU " + id + " no pude entrar a las caches de datos de " + cache_1 + " y " + cache_2 + "!!!");
                                                }
                                            }
                                        } finally {
                                            candados_directorios[memoria_compartida_CPU].unlock();
                                        }
                                    } else {
                                        pc = pc - 4;
                                        System.out.println("Desde el CPU " + id + " no pude entrar al directorio del bloque a escribir!!!");
                                    }
                                }
                            } finally {
                                candados_directorios[memoria_compartida_CPU].unlock();
                            }
                        } else {
                            pc = pc - 4;
                            System.out.println("Desde el CPU " + id + " no pude entrar al directorio del bloque a escribir!!!");
                        }
                    }
                }
            } finally {
                candados_caches[id].unlock();
            }
        } else {
            pc = pc - 4;
            System.out.println("Desde el CPU " + id + " no pude entrar a mi cache de datos!!!");
        }
    }*/

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
        for (int i = 0; i < cant_hilos; i++) {
            if (hilos_terminados[i] == true) {
                j++;
            }
        }
        if (j == cant_hilos) {
            terminado = true;
        }
        return terminado;
    }

    private void cargar_hilos_memoria() { //carga los hilos MIPS a la memoria del CPU
        int inicioMemoria = 128;

        for (int i = 0; i < hilos.size(); i++) {
            contexto[i][32] = inicioMemoria;
            Path filePath = hilos.get(i).toPath();
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
//        LW(32, 5, 4); //13
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


