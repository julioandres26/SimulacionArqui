import java.io.File;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import javax.swing.JFileChooser;

public class Ventana extends javax.swing.JFrame {

    CyclicBarrier barrera;

    public Ventana(CyclicBarrier barrera) {

        this.barrera = barrera;
        initComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        ventana_buscar_archivos = new javax.swing.JFileChooser();
        label_titulo = new javax.swing.JLabel();
        label_hilos = new javax.swing.JLabel();
        label_quantum = new javax.swing.JLabel();
        slider_hilos = new javax.swing.JSlider();
        slider_quantum = new javax.swing.JSlider();
        cantidad_quantum = new javax.swing.JFormattedTextField();
        button_archivos = new javax.swing.JButton();
        button_aceptar = new javax.swing.JButton();
        cantidad_hilos = new javax.swing.JFormattedTextField();
        label_archivos = new javax.swing.JLabel();
        nombres_hilos = new javax.swing.JTextField();

        ventana_buscar_archivos.setMultiSelectionEnabled(true);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        label_titulo.setFont(new java.awt.Font("Arial", 1, 18)); // NOI18N
        label_titulo.setText("Proyecto Arqui");

        label_hilos.setText("Hilos");

        label_quantum.setText("Quantum");

        slider_hilos.setMaximum(12);
        slider_hilos.setMinimum(1);
        slider_hilos.setValue(1);
        slider_hilos.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                slider_hilosStateChanged(evt);
            }
        });

        slider_quantum.setMaximum(10000);
        slider_quantum.setMinimum(1);
        slider_quantum.setValue(1);
        slider_quantum.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                slider_quantumStateChanged(evt);
            }
        });

        cantidad_quantum.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#0"))));
        cantidad_quantum.setText("1");
        cantidad_quantum.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                cantidad_quantumKeyTyped(evt);
            }
        });

        button_archivos.setText("Seleccionar archivos...");
        button_archivos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                button_archivosActionPerformed(evt);
            }
        });

        button_aceptar.setText("Aceptar");
        button_aceptar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                button_aceptarActionPerformed(evt);
            }
        });

        cantidad_hilos.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#0"))));
        cantidad_hilos.setText("1");
        cantidad_hilos.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                cantidad_hilosKeyTyped(evt);
            }
        });

        label_archivos.setText("Archivos");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addGap(160, 160, 160)
                                .addComponent(label_titulo)
                                .addGap(0, 0, Short.MAX_VALUE))
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(layout.createSequentialGroup()
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addGroup(layout.createSequentialGroup()
                                                                .addComponent(label_hilos)
                                                                .addGap(53, 53, 53)
                                                                .addComponent(slider_hilos, javax.swing.GroupLayout.DEFAULT_SIZE, 315, Short.MAX_VALUE))
                                                        .addGroup(layout.createSequentialGroup()
                                                                .addComponent(label_quantum)
                                                                .addGap(31, 31, 31)
                                                                .addComponent(slider_quantum, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                                                .addGap(18, 18, 18)
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addComponent(cantidad_hilos, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(cantidad_quantum, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                                .addComponent(label_archivos)
                                                .addGap(34, 34, 34)
                                                .addComponent(nombres_hilos))
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                                .addGap(0, 0, Short.MAX_VALUE)
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addComponent(button_aceptar, javax.swing.GroupLayout.Alignment.TRAILING)
                                                        .addComponent(button_archivos, javax.swing.GroupLayout.Alignment.TRAILING))))
                                .addContainerGap())
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(label_titulo)
                                .addGap(25, 25, 25)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(label_hilos)
                                        .addComponent(slider_hilos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(cantidad_hilos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(28, 28, 28)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(label_quantum)
                                        .addComponent(slider_quantum, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(cantidad_quantum, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(50, 50, 50)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(label_archivos)
                                        .addComponent(nombres_hilos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(button_archivos)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 44, Short.MAX_VALUE)
                                .addComponent(button_aceptar)
                                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void slider_hilosStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_slider_hilosStateChanged
        cantidad_hilos.setText(String.valueOf(slider_hilos.getValue()));
    }//GEN-LAST:event_slider_hilosStateChanged

    private void slider_quantumStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_slider_quantumStateChanged
        cantidad_quantum.setText(String.valueOf(slider_quantum.getValue()));
    }//GEN-LAST:event_slider_quantumStateChanged

    private void cantidad_quantumKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_cantidad_quantumKeyTyped
        String text = cantidad_quantum.getText();
        char c = evt.getKeyChar();
        if(!text.isEmpty() && !Character.isDigit(c)){
            //getToolkit().beep();
            evt.consume();
        }
        int t;
        try {
            t = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            t = 1;
            slider_quantum.setValue(t);
        }
        if(c=='\n')
            slider_quantum.setValue(t);
    }//GEN-LAST:event_cantidad_quantumKeyTyped

    private void button_archivosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_button_archivosActionPerformed
        int respuesta = ventana_buscar_archivos.showOpenDialog(this);
        if (respuesta == JFileChooser.APPROVE_OPTION) {
            File[] archivos = ventana_buscar_archivos.getSelectedFiles();
            String archivos_text = "";
            for (File archivo : archivos) {
                archivos_text += ("\"" + archivo.getName() + "\" ");
            }
            nombres_hilos.setText(archivos_text);
        }
    }//GEN-LAST:event_button_archivosActionPerformed

    private void cantidad_hilosKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_cantidad_hilosKeyTyped
        String text = cantidad_hilos.getText();
        char c = evt.getKeyChar();
        if(!text.isEmpty() && !Character.isDigit(c)){
            //getToolkit().beep();
            evt.consume();
        }
        int t;
        try {
            t = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            t = 1;
            slider_hilos.setValue(t);
        }
        if(c=='\n')
            slider_hilos.setValue(t);
    }//GEN-LAST:event_cantidad_hilosKeyTyped

    private void button_aceptarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_button_aceptarActionPerformed
        this.dispose();
        try {
            barrera.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            //...
        }
    }//GEN-LAST:event_button_aceptarActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton button_aceptar;
    private javax.swing.JButton button_archivos;
    public javax.swing.JFormattedTextField cantidad_hilos;
    public javax.swing.JFormattedTextField cantidad_quantum;
    private javax.swing.JLabel label_archivos;
    private javax.swing.JLabel label_hilos;
    private javax.swing.JLabel label_quantum;
    private javax.swing.JLabel label_titulo;
    private javax.swing.JTextField nombres_hilos;
    private javax.swing.JSlider slider_hilos;
    private javax.swing.JSlider slider_quantum;
    public javax.swing.JFileChooser ventana_buscar_archivos;
    // End of variables declaration//GEN-END:variables


}
