import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// GLOSARIO DE ETIQUETAS
// Etiquetas caché: C = 0, M = 1, I = 2.
// Etiquetas directorios: C = 0, M = 1, U = 2.

public class Main {

    public static void main(String[] args) throws InterruptedException, BrokenBarrierException {
        CyclicBarrier barreraGUI = new CyclicBarrier(2); //barrera para la interfaz de usuario

        Ventana ventana = new Ventana(barreraGUI);
        ventana.setVisible(true);

        barreraGUI.await();

        // DESCOMENTAR ESTO PARA PODER UTILIZAR LA INTERFAZ GRAFICA
//        int quantum = Integer.parseInt(ventana.cantidad_quantum.getText());
//        int hilos = Integer.parseInt(ventana.cantidad_hilos.getText());
//        File[] archivos = ventana.ventana_buscar_archivos.getSelectedFiles();
        // DESCOMENTAR HASTA AQUI.
        
        // Comentar esta seccion en caso de querer usar la GUI
        int quantum = 30;
        int hilos = 3;
        File[] archivos = new File[3];
        archivos[0] = new File("/Users/Julio/3HilillossoloconLWs-v4/A.txt");
        archivos[1] = new File("/Users/Julio/3HilillossoloconLWs-v4/B.txt");
        archivos[2] = new File("/Users/Julio/3HilillossoloconLWs-v4/C.txt");
//        archivos[3] = new File("C:/Users/A71279/Documents/4.txt");
//        archivos[4] = new File("C:/Users/A71279/Documents/5.txt");
//        archivos[5] = new File("C:/Users/A71279/Documents/6.txt");
        //Fin de seccion

        List<File> archivosCPU1 = new ArrayList<>();
        List<File> archivosCPU2 = new ArrayList<>();
        List<File> archivosCPU3 = new ArrayList<>();

        // [# de cache][# de indice][0->3 = palabras, 4 = etiqueta, 5 = estado]
        int caches_de_datos[][][] = new int[3][4][6];
        for (int i = 0; i < 3; i++){
            for (int j = 0; j < 4; j++){
                for (int k = 0; k < 4; k++){
                    caches_de_datos[i][j][k] = 0;
                }
                caches_de_datos[i][j][4] = -1;
                caches_de_datos[i][j][5] = 2;
            }
        }

        // [# de memoria compartida][32 enteros/palabras, representan 8 bloques que representan 128 bytes]
        int memorias_compartidas[][] = new int [3][32];
        for (int i = 0; i < 3; i++){
            for (int j = 0; j < 32; j++){
                memorias_compartidas[i][j] = 1;
            }
        }

        // [# de directorio][# de bloque][0->2 = # de procesador, 3 = etiqueta]
        int directorios[][][] = new int[3][8][4];
        for (int i = 0; i < 3; i++){
            for (int j = 0; j < 8; j++){
                directorios[i][j][3] = 2;
            }
        }

        // array con los RLs de los tres procesadores
        int registrosRL[] = new int[3];

        Lock candados_directorios[] = new ReentrantLock[3];
        Lock candados_caches[] = new ReentrantLock[3];

        for (int i = 0; i <= 2; i++){
            candados_directorios[i] = new ReentrantLock();
            candados_caches[i] = new ReentrantLock();
        }

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
        ventana.resultados.setBounds(0, 0, 520, 610);
        ventana.informacion.setText("Hilos MIPS " + hilos + ", Quantum " + quantum);

        CyclicBarrier barrera = new CyclicBarrier(4); //barrera para la sincronización de los CPU

        CPU cpu1 = new CPU(0, quantum, archivosCPU1, barrera, caches_de_datos, memorias_compartidas, directorios, candados_caches, candados_directorios, registrosRL);
        CPU cpu2 = new CPU(1, quantum, archivosCPU2, barrera, caches_de_datos, memorias_compartidas, directorios, candados_caches, candados_directorios, registrosRL);
        CPU cpu3 = new CPU(2, quantum, archivosCPU3, barrera, caches_de_datos, memorias_compartidas, directorios, candados_caches, candados_directorios, registrosRL);
        
        Thread thread1 = new Thread(cpu1);
        Thread thread2 = new Thread(cpu2);
        Thread thread3 = new Thread(cpu3);

        thread1.start();
        thread2.start();
        thread3.start();

        int cpu1_reloj[][] = new int[hilos][2]; //reloj de inicio y final de ejecución de los hilos en CPU 1
        int cpu2_reloj[][] = new int[hilos][2]; //reloj de inicio y final de ejecución de los hilos en CPU 2
        int cpu3_reloj[][] = new int[hilos][2]; //reloj de inicio y final de ejecución de los hilos en CPU 3
        
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
        ventana.jTextArea1.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        ventana.jTextArea1.append(cpu1.imprimir_resultados());
        for(int i = 0; i < hilos/3; i++)
            ventana.jTextArea1.append("\nHilo " + cpu1.hilos.get(i).getName() + " empezó con el reloj "
                    + cpu1_reloj[i][0] + " terminó en " + cpu1_reloj[i][1] + ".");
        ventana.jTextArea1.append("\n\n\n");

        ventana.jTextArea1.append(cpu2.imprimir_resultados());
        for(int i = 0; i < hilos/3; i++)
            ventana.jTextArea1.append("\nHilo " + cpu2.hilos.get(i).getName() + " empezó con el reloj "
                    + cpu2_reloj[i][0] + " terminó en " + cpu2_reloj[i][1] + ".");
        ventana.jTextArea1.append("\n\n\n");

        ventana.jTextArea1.append(cpu3.imprimir_resultados());
        for(int i = 0; i < hilos/3; i++)
            ventana.jTextArea1.append("\nHilo " + cpu3.hilos.get(i).getName() + " empezó con el reloj "
                    + cpu3_reloj[i][0] + " terminó en " + cpu3_reloj[i][1] + ".");

        ventana.jTextArea1.append("\n\n\n\n- - - CONTENIDO DE LA MEMORIA COMPARTIDA - - -\n\n");
        ventana.jTextArea1.append("CPU 1"+"\t\t\tCPU 2"+"\t\t\tCPU 3\n\n");
        String format = "%1$-6s %2$-4s \t\t%3$-6s %4$-4s \t\t%5$-6s %6$-4s\n";
        for (int i = 0; i < 32; i++) {
            ventana.jTextArea1.append(String.format(format, i + "->", memorias_compartidas[0][i], (i+32) + "->", memorias_compartidas[1][i], (i+64) + "->", memorias_compartidas[2][i]));
        }

        barreraGUI.await();
        
        System.exit(0);
    }
}
