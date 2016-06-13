import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Main {

    public static void main(String[] args) throws InterruptedException, BrokenBarrierException {
        CyclicBarrier barreraGUI = new CyclicBarrier(2); //barrera para la interfaz de usuario

        Ventana ventana = new Ventana(barreraGUI);
        ventana.setVisible(true);

        barreraGUI.await();

        //int quantum = Integer.parseInt(ventana.cantidad_quantum.getText());
        //int hilos = Integer.parseInt(ventana.cantidad_hilos.getText());
        //File[] archivos = ventana.ventana_buscar_archivos.getSelectedFiles();
        
        // Comentar esta seccion en caso de querer usar la GUI
        int quantum = 30;
        int hilos = 6;
        File[] archivos = new File[6];
        archivos[0] = new File("C:/Users/A71279/Documents/1.txt");
        archivos[1] = new File("C:/Users/A71279/Documents/2.txt");
        archivos[2] = new File("C:/Users/A71279/Documents/3.txt");
        archivos[3] = new File("C:/Users/A71279/Documents/4.txt");
        archivos[4] = new File("C:/Users/A71279/Documents/5.txt");
        archivos[5] = new File("C:/Users/A71279/Documents/6.txt");
        //Fin de seccion

        List<File> archivosCPU1 = new ArrayList<>();
        List<File> archivosCPU2 = new ArrayList<>();
        List<File> archivosCPU3 = new ArrayList<>();

        int caches_dato1[][] = new int[4][6];
        int caches_dato2[][] = new int[4][6];
        int caches_dato3[][] = new int[4][6];
        
        int memoria_compartida1[] = new int[32];
        int memoria_compartida2[]= new int[32];
        int memoria_compartida3[] = new int[32];
        
        Lock lock_cache_datos1 = new ReentrantLock();
        Lock lock_cache_datos2 = new ReentrantLock();
        Lock lock_cache_datos3 = new ReentrantLock();
        
        Lock lock_memoria_compartida1 = new ReentrantLock();
        Lock lock_memoria_compartida2 = new ReentrantLock();
        Lock lock_memoria_compartida3 = new ReentrantLock();

        int temporal = 1;
        for (int i = 0; i < archivos.length; i++) { //se reparten los archivos a cada CPU
            if (temporal == 4) {
                temporal = 1;
            }
            switch (temporal) {
                case 1:
                    archivosCPU1.add(archivos[i]);
                    temporal++;
                    break;
                case 2:
                    archivosCPU2.add(archivos[i]);
                    temporal++;
                    break;
                case 3:
                    archivosCPU3.add(archivos[i]);
                    temporal++;
                    break;
            }
        }

        ventana.resultados.setVisible(true);
        ventana.resultados.setBounds(0, 0, 468, 590);
        ventana.informacion.setText("Hilos MIPS " + hilos + ", Quantum " + quantum);

        CyclicBarrier barrera = new CyclicBarrier(4); //barrera para la sincronización de los CPU

        CPU cpu1 = new CPU(1, quantum, archivosCPU1, barrera);
        CPU cpu2 = new CPU(2, quantum, archivosCPU2, barrera);
        CPU cpu3 = new CPU(3, quantum, archivosCPU3, barrera);
        
        cpu1.recibir_caches(caches_dato1, caches_dato2, caches_dato3);
        cpu2.recibir_caches(caches_dato1, caches_dato2, caches_dato3);
        cpu3.recibir_caches(caches_dato1, caches_dato2, caches_dato3);
        
        cpu1.recibir_lock_caches(lock_cache_datos1, lock_cache_datos2, lock_cache_datos3);
        cpu2.recibir_lock_caches(lock_cache_datos1, lock_cache_datos2, lock_cache_datos3);
        cpu3.recibir_lock_caches(lock_cache_datos1, lock_cache_datos2, lock_cache_datos3);
        
        cpu1.recibir_memorias_compartidas(memoria_compartida1, memoria_compartida2, memoria_compartida3);
        cpu2.recibir_memorias_compartidas(memoria_compartida1, memoria_compartida2, memoria_compartida3);
        cpu3.recibir_memorias_compartidas(memoria_compartida1, memoria_compartida2, memoria_compartida3);
        
        cpu1.recibir_lock_memorias(lock_memoria_compartida1, lock_memoria_compartida2, lock_memoria_compartida3);
        cpu2.recibir_lock_memorias(lock_memoria_compartida1, lock_memoria_compartida2, lock_memoria_compartida3);
        cpu3.recibir_lock_memorias(lock_memoria_compartida1, lock_memoria_compartida2, lock_memoria_compartida3);

        Thread thread1 = new Thread(cpu1);
        Thread thread2 = new Thread(cpu2);
        Thread thread3 = new Thread(cpu3);

        thread1.start();
        thread2.start();
        thread3.start();

        int cpu1_reloj[][] = new int[hilos][2]; //reloj de inicio y final de ejecución de los hilos en CPU 1
        int cpu2_reloj[][] = new int[hilos][2]; //reloj de inicio y final de ejecución de los hilos en CPU 2
        int cpu3_reloj[][] = new int[hilos][2]; //reloj de inicio y final de ejecución de los hilos en CPU 3
        
        caches_dato1[2][2] = 17;
        caches_dato1[2][1] = 13;

        int reloj = 0; //reloj de sincronización del hilo padre
        while (!cpu1.procesamiento_terminado() || !cpu2.procesamiento_terminado() || !cpu3.procesamiento_terminado()) {
            barrera.await();

            if (cpu1.reloj[cpu1.hilo_actual] == 0)
                cpu1_reloj[cpu1.hilo_actual][0] = reloj; //reloj de inicio del hilo i en CPU 1
            if (cpu1.hilos_terminados[cpu1.hilo_actual] == false)
                cpu1_reloj[cpu1.hilo_actual][1] = reloj; //reloj de fin del hilo i en CPU 1

            if (cpu2.reloj[cpu2.hilo_actual] == 0)
                cpu2_reloj[cpu2.hilo_actual][0] = reloj; //reloj de inicio del hilo i en CPU 2
            if (cpu2.hilos_terminados[cpu2.hilo_actual] == false)
                cpu2_reloj[cpu2.hilo_actual][1] = reloj; //reloj de fin del hilo i en CPU 2

            if (cpu3.reloj[cpu3.hilo_actual] == 0)
                cpu3_reloj[cpu3.hilo_actual][0] = reloj; //reloj de inicio del hilo i en CPU 3
            if (cpu3.hilos_terminados[cpu3.hilo_actual] == false)
                cpu3_reloj[cpu3.hilo_actual][1] = reloj; //reloj de fin del hilo i en CPU 3

            reloj++;

            barrera.await();
        }

        barrera.await();

        //impresión de los resultados en la ventana
        ventana.jTextArea1.append(cpu1.imprimir_resultados() + "\n");
        for(int i = 0; i < hilos/3; i++)
            ventana.jTextArea1.append("\nHilo " + cpu1.hilos.get(i).getName() + " empezó con el reloj "
                    + cpu1_reloj[i][0] + " terminó en " + cpu1_reloj[i][1] + ".");
        ventana.jTextArea1.append("\n\n\n");

        ventana.jTextArea1.append(cpu2.imprimir_resultados() + "\n");
        for(int i = 0; i < hilos/3; i++)
            ventana.jTextArea1.append("\nHilo " + cpu2.hilos.get(i).getName() + " empezó con el reloj "
                    + cpu2_reloj[i][0] + " terminó en " + cpu2_reloj[i][1] + ".");
        ventana.jTextArea1.append("\n\n\n");

        ventana.jTextArea1.append(cpu3.imprimir_resultados() + "\n");
        for(int i = 0; i < hilos/3; i++)
            ventana.jTextArea1.append("\nHilo " + cpu3.hilos.get(i).getName() + " empezó con el reloj "
                    + cpu3_reloj[i][0] + " terminó en " + cpu3_reloj[i][1] + ".");

        barreraGUI.await();
        
        System.out.println("Desde el main: " + cpu1.prueba);
        System.exit(0);
    }
}
