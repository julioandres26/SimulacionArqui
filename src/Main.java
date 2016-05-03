/**
 * Created by Julio on 5/2/16.
 */

import java.util.Scanner;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class Main {

    public static void main(String[] args){

        Scanner in = new Scanner(System.in);
        System.out.println("Ingrese el valor del quantum: ");
        int quantum = Integer.parseInt(in.nextLine());

        CyclicBarrier barrera = new CyclicBarrier(4);

        CPU cpu1 = new CPU(1, quantum, barrera);
//        CPU cpu2 = new CPU(2, quantum, barrera);
//        CPU cpu3 = new CPU(2, quantum, barrera);

        Thread thread1 = new Thread(cpu1);
//        Thread thread2 = new Thread(cpu2);
//        Thread thread3 = new Thread(cpu3);

        thread1.start();
//        thread2.start();
//        thread3.start();

//        while(true){
//            if(!cpu1.procesamientoTerminado() && !cpu2.procesamientoTerminado() && !cpu3.procesamientoTerminado()){
//                try {
//                    barrera.await();
//                } catch (InterruptedException | BrokenBarrierException e) {
//                    //...
//                }
//            } else{
//                break;
//            }
//        }
    }
}
