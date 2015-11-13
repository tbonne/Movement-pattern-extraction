package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureCollection;
import org.opengis.feature.simple.SimpleFeature;


public class ParameterLoader extends JFrame{

	private final int hGap = 5;
    private final int vGap = 5;
    private JPanel focalPanel;
    private JPanel groupPanel;
    private JPanel endPanel;
    private JPanel outputPanel,testPanel;
    
	
	private JTextField timeField_GM, idField_GM,timeField_F, idField_F, maxTField,groupField,focalTextField,groupTextField,outShpTextField;
	private JTextField testTimeF,testIDF,testTimeGM,testIDGM;
	private static String timeC_F,timeC_GM;
	private static String idC_F,idC_GM;
	private static String maxTC, group;
	private static String focalFile, groupFile, outFile;
	private static ArrayList waitingBool;
	
	static SimpleFeature point_F=null;
	static SimpleFeature point_GM=null;

	public ParameterLoader() {
		setTitle("Group movement-pattern-extraction tool");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		//setLayout(new GridLayout(9,1,2,2));
		
		JPanel contentPane = new JPanel(
                new GridLayout(0, 1, hGap, vGap));
		contentPane.setBorder(
	            BorderFactory.createEmptyBorder(hGap, vGap, hGap, vGap));
		
		//object used to control waiting time
		waitingBool = new ArrayList();

		//text input fields
		timeField_F = new JTextField(5);
		idField_F = new JTextField(5);
		timeField_GM = new JTextField(5);
		idField_GM = new JTextField(5);
		groupField = new JTextField(5);
		maxTField = new JTextField(5);
		
		testTimeF = new JTextField(5);
		testIDF = new JTextField(5);
		testTimeGM = new JTextField(5);
		testIDGM = new JTextField(5);
		
		//create buttons
		JButton focalB = new JButton("Focal shapefile");
		JButton groupB = new JButton("Group shapefile");
		JButton outShpB = new JButton("Output shapefile");
		JButton myButton = new JButton("RUN");
		JButton testB = new JButton("Test columns");
		
		class ButtonListener implements ActionListener {
			public void actionPerformed(ActionEvent ae) {
				
				if(ae.getSource()==myButton){
				timeC_F=	timeField_F.getText();//Integer.parseInt(timeField_F.getText());
				idC_F=	idField_F.getText();//Integer.parseInt(idField_F.getText());
				timeC_GM=	timeField_GM.getText();//Integer.parseInt(timeField_GM.getText());
				idC_GM= idField_GM.getText();//	Integer.parseInt(idField_GM.getText());
				group=	groupField.getText();//Integer.parseInt(groupField.getText());
				maxTC=	maxTField.getText();//Integer.parseInt(maxTField.getText());
				synchronized (waitingBool){waitingBool.notify();}
				setVisible(false); 
				dispose(); 
				}
				if(ae.getSource()==focalB){
					//load focal animal shapefile
					JFileChooser fc = new JFileChooser();
					FileNameExtensionFilter filter = new FileNameExtensionFilter(
							"shapefiles", "shp");
					fc.setDialogTitle("Select observations from the focal animal");
					fc.setFileFilter(filter);

					if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
						focalFile = fc.getSelectedFile().getAbsolutePath();
						focalTextField.setText(fc.getSelectedFile().getAbsolutePath());
					} else {
						System.out.println("No Selection ");
					}
				}
				if(ae.getSource()==groupB){
					//load focal animal shapefile
					JFileChooser fc = new JFileChooser();
					FileNameExtensionFilter filter = new FileNameExtensionFilter(
							"shapefiles", "shp");
					fc.setDialogTitle("Select observations from the group");
					fc.setFileFilter(filter);

					if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
						groupFile = fc.getSelectedFile().getAbsolutePath();
						groupTextField.setText(fc.getSelectedFile().getAbsolutePath());
					} else {
						System.out.println("No Selection ");
					}
				}
				if(ae.getSource()==outShpB){
					//Output shapefile
					JFileChooser fc = new JFileChooser();
					FileNameExtensionFilter filter = new FileNameExtensionFilter(
							"shapefiles", "shp");
					fc.setDialogTitle("Specify output shapefile");
					fc.setFileFilter(filter);

					if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
						outFile = fc.getSelectedFile().getAbsolutePath();
						outShpTextField.setText(fc.getSelectedFile().getAbsolutePath());
					} else {
						System.out.println("No Selection ");
					}
				}
				
				if(ae.getSource()==testB){
					inputSample();
					int timeC_F=	Integer.parseInt(timeField_F.getText());
					int idC_F=	Integer.parseInt(idField_F.getText());
					int timeC_GM=	Integer.parseInt(timeField_GM.getText());
					int idC_GM= 	Integer.parseInt(idField_GM.getText());
					
					try{
					testTimeF.setText(  (point_F.getAttribute(timeC_F)).toString() );
					testIDF.setText(((Integer) point_F.getAttribute(idC_F)).toString());
					testTimeGM.setText((point_GM.getAttribute(timeC_GM)).toString());
					testIDGM.setText(((Integer) point_GM.getAttribute(idC_GM)).toString());
					}catch (ClassCastException e){
						JOptionPane.showMessageDialog(testPanel, "A column is not properly set: "+e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
					}
				}
			}
		}
		
		//add buttons + text
		focalPanel = new JPanel(new GridLayout(4,2,hGap, vGap));
        focalPanel.setBorder(
            BorderFactory.createTitledBorder("Focal animal positions"));
        focalPanel.setOpaque(true);
        focalPanel.setBackground(Color.WHITE);
        
		focalB.addActionListener(new ButtonListener());
		focalPanel.add(focalB);
		focalTextField =  new JTextField(10);
		focalPanel.add(focalTextField);
		focalPanel.add(new JLabel("Time Column (Focal):"));
		focalPanel.add(timeField_F);
		focalPanel.add(new JLabel("ID Column (Focal):"));
		focalPanel.add(idField_F);
		focalPanel.add(new JLabel("Maximum time between observations (minutes):"));
		focalPanel.add(maxTField);
		
		groupPanel = new JPanel(new GridLayout(4,2,hGap, vGap));
		groupPanel.setBorder(
            BorderFactory.createTitledBorder("Groupmate positions"));
		groupPanel.setOpaque(true);
		groupPanel.setBackground(Color.WHITE);
		
		groupB.addActionListener(new ButtonListener());
		groupPanel.add(groupB);
		groupTextField =  new JTextField(10);
		groupPanel.add(groupTextField);
		groupPanel.add(new JLabel("Time Column (GroupMates):"));
		groupPanel.add(timeField_GM);
		groupPanel.add(new JLabel("ID Column (GroupMates):"));
		groupPanel.add(idField_GM);
		groupPanel.add(new JLabel("Group size:"));
		groupPanel.add(groupField);
		
		testPanel = new JPanel(new GridLayout(5,2,hGap, vGap));
		testPanel.setBorder(
            BorderFactory.createTitledBorder("Test whether the columns are properly identified"));
		testPanel.setOpaque(true);
		testPanel.setBackground(Color.WHITE);
		
		testB.addActionListener(new ButtonListener());
		testPanel.add(new JLabel("Time value (focal):"));
		testPanel.add(testTimeF);
		testPanel.add(new JLabel("ID value (focal):"));
		testPanel.add(testIDF);
		testPanel.add(new JLabel("Time value (Groupmates):"));
		testPanel.add(testTimeGM);
		testPanel.add(new JLabel("ID value (Groupmates):"));
		testPanel.add(testIDGM);
		testPanel.add(testB);

		
		/*outShpB.addActionListener(new ButtonListener());
		outputPanel = new JPanel(new GridLayout(4,1,hGap, vGap));
		outputPanel.setBorder(
            BorderFactory.createTitledBorder("Output shapefile"));
		outputPanel.setOpaque(true);
		outputPanel.setBackground(Color.WHITE);
		outputPanel.add(outShpB);
		outShpTextField =  new JTextField(10);
		outputPanel.add(outShpTextField);*/
		
		endPanel = new JPanel(new GridLayout(4,1));
        //endPanel.setBorder(
         //   BorderFactory.createTitledBorder("Focal animal posistions"));
        //endPanel.setOpaque(true);
        //endPanel.setBackground(Color.WHITE);
		
		myButton.addActionListener(new ButtonListener());
		endPanel.add(myButton);
		
		
		contentPane.add(focalPanel);
		contentPane.add(groupPanel);
		//contentPane.add(outputPanel);
		contentPane.add(testPanel);
		contentPane.add(endPanel);
		setContentPane(contentPane);
		setLocationByPlatform(true);
		pack();
		setVisible(true);

		synchronized(waitingBool){
			try {
				waitingBool.wait();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	public static String[] getParameters(){
		if(maxTC.length()==0)maxTC="99999";
		String[] r = {timeC_F,idC_F,timeC_GM,idC_GM,group,maxTC,focalFile,groupFile,outFile}; 
		return r;
	}
	
	private static void inputSample(){

		//Load original file first
		File file = new File (focalFile);

		//FeatureCollection points=null;
		

		try {
			FileDataStore store;
			store = FileDataStoreFinder.getDataStore(file);
			SimpleFeatureSource featureSource = store.getFeatureSource();

			//points = featureSource.getFeatures();
			point_F = (SimpleFeature) featureSource.getFeatures().toArray()[0];

		} catch (IOException e) {
			e.printStackTrace();
		}

		//original =  new ArrayList(Arrays.asList(points.toArray()));
		//original =  Arrays.asList(points.toArray()).listIterator();


		//load interpolated file second
		File file_interp = new File (groupFile);

		//FeatureCollection points_interp=null; 

		try {
			FileDataStore store_interp;
			store_interp = FileDataStoreFinder.getDataStore(file_interp);
			SimpleFeatureSource featureSource_interp = store_interp.getFeatureSource();

			//points_interp = featureSource_interp.getFeatures();
			point_GM = (SimpleFeature) featureSource_interp.getFeatures().toArray()[0];
			//points_interp.sort(SortBy.NATURAL_ORDER);

		} catch (IOException e) {
			e.printStackTrace();
		}

		//original_interpolated = points_interp.features();

	}




}
