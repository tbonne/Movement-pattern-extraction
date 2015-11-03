package behaviour;

import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import ui.ParameterLoader;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;

public class Extract_trajectory {

	/*****************************************************
	 * 
	 * This class runs with a focal animal position, and the interpolated positions of other group members at the same observation time 
	 * requirments:
	 * rows ordered in increasing time
	 * Points should have an id field, time field
	 * 
	 *  This method calculates the observed dx dy of the unit vector of travel,
	 *  the distance and unit vector (dx,dy) to each group member
	 * 
	 * Assumptions
	 * -if the position has not changed (i.e. distance =0) then the observation point is not included 
	 * 
	 ******************************************************/


	//movement data
	static String original_shapefile = "focal.shp"; 			//focal animal observations
	static String original_shapefile_intepolated = "group.shp"; //all animal observations (interpolated to focal animal observations)

	static ArrayList<SimpleFeature> original;
	static FeatureIterator original_interpolated;

	//record the following properties
	static ArrayList<Double> obsY_list;
	static ArrayList<Double> obsX_list;
	static ArrayList<Double> bearingX_list;
	static ArrayList<Double> bearingY_list;
	static ArrayList<Double> resultantX_list;
	static ArrayList<Double> resultantY_list;
	static ArrayList<Double> resultantMag_list;
	static ArrayList<Double> iid_list;
	static ArrayList<ArrayList> vectorX_list;
	static ArrayList<ArrayList> vectorY_list;
	static ArrayList<ArrayList> distance_list;
	static ArrayList<Double> revisitTime_list;
	static ArrayList<LocalDateTime> time_list;
	static ArrayList<Double> focalX_list;
	static ArrayList<Double> focalY_list;

	//output records
	static String outputCSV = "tradjectoryOUT.csv";
	static String output_shapefile = "shapeTrajectoryOUT.shp";

	//set paramters for each run
	static int s_time_interp = 3;   	//interpolated points time 
	static  int s_id_interp = 2;    	//interpolated id
	static  int s_time = 22;			//focal time
	static  int s_id = 7;				//focal id
	static  int groupSize = 14; 		//number of individuals within the group
	static  int time_max = 30*60;    	//pairs of observations beyond this time limit are not considered 

	//set the time format 
	static DateTimeFormatter formatter_interp = DateTimeFormatter.ofPattern("M/d/yyyy'T'H:mm", Locale.ENGLISH);//DateTimeFormatter.ofPattern("yyyy-M-d'T'H:mm", Locale.ENGLISH);
	static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy'T'H:mm", Locale.ENGLISH);//DateTimeFormatter.ofPattern("M/d/yyyy'T'H:mm", Locale.ENGLISH);
	static SimpleFeature previous_sf=null;

	
	public static void main(String[] args){

		//set variables
		getUserVariables();

		//store variables
		obsY_list = new ArrayList<Double>();
		obsX_list = new ArrayList<Double>();
		bearingX_list = new ArrayList<Double>();
		bearingY_list = new ArrayList<Double>();
		resultantX_list = new ArrayList<Double>();
		resultantY_list = new ArrayList<Double>();
		resultantMag_list = new ArrayList<Double>();
		revisitTime_list = new ArrayList<Double>();
		vectorX_list = new ArrayList<ArrayList>();
		vectorY_list = new ArrayList<ArrayList>();
		distance_list = new ArrayList<ArrayList>();
		iid_list = new ArrayList<Double>();
		time_list = new ArrayList<LocalDateTime>();
		focalX_list = new ArrayList<Double>();
		focalY_list = new ArrayList<Double>();

		//import data
		inputData();

		//extraction
		try{
		extractVectors();
		} catch (Exception e){
			JPanel err = new JPanel();
			JOptionPane.showMessageDialog(err,"Error: "+e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}

		//export error to shapefile
		//exportShapefile();
		
		//export to CSV
		exportCSV();

	}

	/*********************************************main methods*************************************/
	private static void extractVectors() throws ClassCastException{

		//focal individual position
		ArrayList<SimpleFeature> original_sub = original;

		SimpleFeature focal = null,focal_t1=null,focal_tneg1 =null;
		double angle_pred=0,time_delta=0,time_delta_back=0,e=0.0;
		RealVector angle_obs=null;

		//at each observation point for the focal animal, record the distance and unit vector (dx,dy) to all others, and mean resultant.
		for(int j=1;j<original_sub.size()-1;j++){ 

			//for each individual point find information about t0
			focal = original_sub.get(j);
			focal_t1 = original_sub.get(j+1);
			focal_tneg1 = original_sub.get(j-1);

			time_delta = getDateOriginal(focal).until(getDateOriginal(focal_t1), ChronoUnit.SECONDS);
			time_delta_back = getDateOriginal(focal_tneg1).until(getDateOriginal(focal), ChronoUnit.SECONDS);

			double time_delta_max = Math.max(time_delta, time_delta_back);

			if(time_delta_max<time_max){

				//control for when no motion is observed (cannot predict motion)
				double distanceTraveled_before = getDistance(focal,focal_tneg1);
				double distanceTraveled_after = getDistance(focal,focal_t1); 

				if(distanceTraveled_before>0&&distanceTraveled_after>0){
					angle_obs = getVector(focal,focal_t1);
					RealVector last_bearing = getVector(focal_tneg1,focal);
					ArrayList<SimpleFeature> groupmates_t0 = getGroupMates(focal);
					Coordinate focalC = ((Point)focal.getDefaultGeometry()).getCoordinate();

					//add where and when observation is made
					focalX_list.add(focalC.x);
					focalY_list.add(focalC.y);
					time_list.add(getDateOriginal(focal));

					//add bearing from last point
					bearingX_list.add(last_bearing.unitVector().getEntry(0));
					bearingY_list.add(last_bearing.unitVector().getEntry(1));

					//add bearing to next point
					obsX_list.add(angle_obs.unitVector().getEntry(0));
					obsY_list.add(angle_obs.unitVector().getEntry(1));

					//add direction and magnitude of mean resultant
					RealVector resultantV = getMeanResultant(groupmates_t0,focal);
					if(resultantV!=null){
						double resultant = getMeanResultantMag(groupmates_t0,focal);
						resultantMag_list.add(resultant);
						resultantX_list.add(resultantV.unitVector().getEntry(0));
						resultantY_list.add(resultantV.unitVector().getEntry(1));
					} else {
						resultantMag_list.add(null);
						resultantX_list.add(null);
						resultantY_list.add(null);
					}

					//add distance and direction to each groupmate
					ArrayList<Double> vectorsX = new ArrayList<Double>();
					ArrayList<Double> vectorsY = new ArrayList<Double>();
					ArrayList<Double> distance = new ArrayList<Double>();

					for(SimpleFeature sf : groupmates_t0){
						if(sf!=null){

							RealVector toMate = getVector(focal,sf);
							double dist = getDistance(focal,sf);

							if(dist>0){
								vectorsX.add(toMate.unitVector().getEntry(0));
								vectorsY.add(toMate.unitVector().getEntry(1));
							}else{
								vectorsX.add(null);
								vectorsY.add(null);
							}

							distance.add(dist);

						} else {
							//System.out.println("what?!?!");
							vectorsX.add(null);
							vectorsY.add(null);
							distance.add(null);
						}
					}
					vectorX_list.add(vectorsX);
					vectorY_list.add(vectorsY);
					distance_list.add(distance);


					//add revisit times (variance increases as revisit times are longer?)
					revisitTime_list.add(time_delta+Math.abs(time_delta_back));

					//add inter-individual distance
					double resultantDist = getIID(focal,groupmates_t0);
					iid_list.add(resultantDist);
				}
			}
		}

	}

	/*********************************************secondary methods*************************************/
	private static ArrayList<SimpleFeature> getSubset(int id){
		ArrayList<SimpleFeature> sub = new ArrayList<SimpleFeature>();
		for(int i=0;i<original.size();i++){
			SimpleFeature sf = original.get(i);
			if( ((Double)sf.getAttribute(s_id)).intValue()==id)sub.add(sf);
			//if( ((Integer)sf.getAttribute(s_id)).intValue()==id)sub.add(sf);
		}

		return sub;
	}

	public static double getIID(SimpleFeature focal, ArrayList<SimpleFeature> gm){
		double dist=0;
		int count=0;

		for (SimpleFeature groupMate: gm){
			if(groupMate!=null){
				//if( (Long)groupMate.getAttribute(s_id)!=(Long)focal.getAttribute(s_id) ){
				if( (Integer)groupMate.getAttribute(s_id_interp)!=((Integer)focal.getAttribute(s_id)).intValue() ){
					count++;
					Coordinate gmC = ((Point)groupMate.getAttribute(0)).getCoordinate();
					Coordinate fC = ((Point)focal.getAttribute(0)).getCoordinate();
					dist = dist + gmC.distance(fC);
				}
			}
		}
		return 	dist/(double)count;  
	}

	public static double getDistance(SimpleFeature sf1, SimpleFeature sf2){

		double distance = 0;

		Coordinate source = ((Point)sf1.getDefaultGeometry()).getCoordinate();
		Coordinate target = ((Point)sf2.getDefaultGeometry()).getCoordinate();

		distance = source.distance(target);

		return distance;

	}

	private static ArrayList<SimpleFeature> getGroupMates(SimpleFeature focal) throws ClassCastException{

		ArrayList<SimpleFeature> groupMates = new ArrayList<SimpleFeature>();
		for(int i=0;i<14;i++){
			groupMates.add(null);
		}
		while(original_interpolated.hasNext()){

			//used to test whether the point previous to the current index in the iterator is at the same time as the focal observation 
			if(previous_sf!=null){
				double time = getDateOriginal(focal).until(getDateInterpolated(previous_sf), ChronoUnit.MINUTES);
				if(time==0 && (Integer)previous_sf.getAttribute(s_id_interp)!=((Integer)focal.getAttribute(s_id)).intValue()){
					groupMates.set((Integer)previous_sf.getAttribute(s_id_interp), previous_sf);
				}
			}

			SimpleFeature sf = (SimpleFeature) original_interpolated.next();
			if( (((Integer) sf.getAttribute(s_id_interp)))!= (((Integer) focal.getAttribute(s_id)))){ 
				//double time = (Integer) focal.getAttribute(s_time)-(Integer) sf.getAttribute(s_time_interp);
				double time = getDateOriginal(focal).until(getDateInterpolated(sf), ChronoUnit.MINUTES);
				if(time==0){
					int id = ((Integer)sf.getAttribute(s_id_interp)).intValue();
					groupMates.set(id, sf);
				} else if (time<0){
					//System.out.println("something wrong with the get groupmates method?");
					//interpolated_list.previous();
					//break;
				} else if (time>0){
					//interpolated_list.previous();
					previous_sf = sf;
					break;
				}
			}
		}



		return groupMates;
	}

	public static LocalDateTime getDateInterpolated(SimpleFeature sf){

		String str = ((String) sf.getAttribute(s_time_interp));
		LocalDateTime d =null;
		d = LocalDateTime.parse(str, formatter_interp);

		return d;
	}
	public static LocalDateTime getDateOriginal(SimpleFeature sf){

		String str = ((String) sf.getAttribute(s_time));
		LocalDateTime d = LocalDateTime.parse(str, formatter);

		return d;
	}
	public static float getAngle(SimpleFeature s1, SimpleFeature s2) {

		//get coordinates from simple feature
		Coordinate source = ((Point)s1.getDefaultGeometry()).getCoordinate();
		Coordinate target = ((Point)s2.getDefaultGeometry()).getCoordinate();

		//angle
		float angle = (float) (Math.atan2(target.y - source.y, target.x - source.x));

		if(angle < 0){
			angle += 2*Math.PI;
		}

		return (float) angle;
	}

	public static float getAngle(SimpleFeature s1, Coordinate s2) {

		//get coordinates from simple feature
		Coordinate source = ((Point)s1.getDefaultGeometry()).getCoordinate();

		//angle
		float angle = (float) (Math.atan2(s2.y - source.y, s2.x - source.x));

		if(angle < 0){
			angle += 2*Math.PI;
		}

		return (float) angle;
	}

	public static RealVector getVector(SimpleFeature s1, SimpleFeature s2) {
		//get coordinates from simple feature
		Coordinate source = ((Point)s1.getDefaultGeometry()).getCoordinate();
		Coordinate target = ((Point)s2.getDefaultGeometry()).getCoordinate();
		RealVector rv = new ArrayRealVector(new double[] { target.x - source.x, target.y - source.y }, false);
		return rv;
	}

	public static RealVector getMeanResultant(ArrayList<SimpleFeature> groupmates, SimpleFeature focal){

		double cosAngle=0,sinAngle=0;
		int count=0;

		for (SimpleFeature groupMate: groupmates){

			if(groupMate!=null){
				double angle = getAngle(focal,groupMate);
				cosAngle = cosAngle + Math.cos(angle);
				sinAngle = sinAngle + Math.sin(angle);
				count++;
			}
		}
		RealVector mRV=null;
		if(count>0){
			mRV = new ArrayRealVector(new double[] { cosAngle, sinAngle }, false);
		} else{
			//System.out.println("something is wrong with the count in mean resultant... no groupmates");
		}

		return mRV;
	}

	public static double getMeanResultantMag(ArrayList<SimpleFeature> groupmates, SimpleFeature focal){

		double cosAngle=0,sinAngle=0;
		int count=0;
		double mR = 0;

		for (SimpleFeature groupMate: groupmates){

			if(groupMate!=null){
				double angle = getAngle(focal,groupMate);
				cosAngle = cosAngle + Math.cos(angle);
				sinAngle = sinAngle + Math.sin(angle);
				count++;
			}
		}

		if(count>0){
			mR = Math.pow( Math.pow(cosAngle,2) + Math.pow(sinAngle,2) , 0.5) / ((double)count);
		} else{
			//System.out.println("something is wrong with the count in mean resultant... no groupmates");
		}

		return mR;
	}

	/************************************* UI ************************************************************/

	private static void getUserVariables(){
		
		//get user parameters
		ParameterLoader pm = new ParameterLoader();
		String[] para = pm.getParameters();

		//set paramters for each run
		s_time_interp = Integer.parseInt(para[2]);
		s_id_interp = Integer.parseInt(para[3]);    //interpolated id
		s_time = Integer.parseInt(para[0]);			//focal time
		s_id = Integer.parseInt(para[1]);			//focal id
		groupSize = Integer.parseInt(para[4]); 		//number of individuals within the group
		
		time_max = Integer.parseInt(para[5])*60;    //pairs of observations beyond this time limit are not considered (sec)
		original_shapefile_intepolated = para[7];
		original_shapefile = para[6];
		output_shapefile = para[8];

	}

	private static void testParam(){
		SimpleFeature test = original.get(0);
		SimpleFeature test_GM = (SimpleFeature) original_interpolated.next();
		System.out.println("first focal observation:");
		System.out.println("time = "+test.getAttribute(s_time)+ ", id = "+test.getAttribute(s_id));
		System.out.println("first groupmate observation:");
		System.out.println("time = "+test_GM.getAttribute(s_time_interp)+ ", id = "+test_GM.getAttribute(s_id_interp));
	}



	/**************************************output*********************************************************/

	private static void exportCSV(){


		//creating a file to store the output of the counts
		BufferedWriter errorStats=null;
		try {
			//create writer
			errorStats = new BufferedWriter(new FileWriter(outputCSV,false));

			//set up header

			errorStats.append("d.obs");
			errorStats.append(",");
			errorStats.append("change");
			errorStats.append(",");
			errorStats.append("d.bearing");
			errorStats.append(",");
			errorStats.append("d.resultant");
			errorStats.append(",");
			errorStats.append("mag.resultant");
			errorStats.append(",");
			errorStats.append("d.0");
			errorStats.append(",");
			errorStats.append("dist.0");
			errorStats.append(",");
			errorStats.append("d.1");
			errorStats.append(",");
			errorStats.append("dist.1");
			errorStats.append(",");
			errorStats.append("d.2");
			errorStats.append(",");
			errorStats.append("dist.2");
			errorStats.append(",");
			errorStats.append("d.3");
			errorStats.append(",");
			errorStats.append("dist.3");
			errorStats.append(",");
			errorStats.append("d.4");
			errorStats.append(",");
			errorStats.append("dist.4");
			errorStats.append(",");
			errorStats.append("d.5");
			errorStats.append(",");
			errorStats.append("dist.5");
			errorStats.append(",");
			errorStats.append("d.6");
			errorStats.append(",");
			errorStats.append("dist.6");
			errorStats.append(",");
			errorStats.append("d.7");
			errorStats.append(",");
			errorStats.append("dist.7");
			errorStats.append(",");
			errorStats.append("d.8");
			errorStats.append(",");
			errorStats.append("dist.8");
			errorStats.append(",");
			errorStats.append("d.9"); 
			errorStats.append(",");
			errorStats.append("dist.9");
			errorStats.append(",");
			errorStats.append("d.10"); 
			errorStats.append(",");
			errorStats.append("dist.10");
			errorStats.append(",");
			errorStats.append("d.11"); 
			errorStats.append(",");
			errorStats.append("dist.11");
			errorStats.append(",");
			errorStats.append("d.12");
			errorStats.append(",");
			errorStats.append("dist.12");
			errorStats.append(",");
			errorStats.append("d.13");
			errorStats.append(",");
			errorStats.append("dist.13");
			errorStats.append(",");
			errorStats.append("RevisitT");
			errorStats.append(",");
			errorStats.append("time");
			errorStats.append(",");
			errorStats.append("x");
			errorStats.append(",");
			errorStats.append("y");
			errorStats.append(",");
			errorStats.append("iid");

			errorStats.newLine();

			//place data in rows
			for(int i =0;i<obsX_list.size();i++){

				errorStats.append(obsX_list.get(i).toString());
				errorStats.append(",");
				errorStats.append("dx");
				errorStats.append(",");
				errorStats.append(bearingX_list.get(i).toString());
				errorStats.append(",");
				if(resultantX_list.get(i)!=null){
					errorStats.append(resultantX_list.get(i).toString());
					errorStats.append(",");
					errorStats.append(resultantMag_list.get(i).toString());
					errorStats.append(",");
				} else {
					errorStats.append("NA");
					errorStats.append(",");
					errorStats.append("NA");
					errorStats.append(",");
				}

				for(int j=0;j<groupSize;j++){
					try{
						errorStats.append(vectorX_list.get(i).get(j).toString());
						errorStats.append(",");
						errorStats.append(distance_list.get(i).get(j).toString());
						errorStats.append(",");
					} catch (NullPointerException e){
						errorStats.append("NA,");
						errorStats.append("NA,");
					}
				}
				errorStats.append(revisitTime_list.get(i).toString());
				errorStats.append(",");
				errorStats.append(time_list.get(i).toString());
				errorStats.append(",");
				errorStats.append(focalX_list.get(i).toString());
				errorStats.append(",");
				errorStats.append(focalY_list.get(i).toString());
				errorStats.append(",");
				errorStats.append(iid_list.get(i).toString());
				errorStats.newLine();
			}
			for(int i =0;i<obsY_list.size();i++){

				errorStats.append(obsY_list.get(i).toString());
				errorStats.append(",");
				errorStats.append("dy");
				errorStats.append(",");
				errorStats.append(bearingY_list.get(i).toString());
				errorStats.append(",");
				if(resultantY_list.get(i)!=null){
					errorStats.append(resultantY_list.get(i).toString());
					errorStats.append(",");
					errorStats.append(resultantMag_list.get(i).toString());
					errorStats.append(",");
				} else {
					errorStats.append("NA");
					errorStats.append(",");
					errorStats.append("NA");
					errorStats.append(",");
				}

				for(int j=0;j<groupSize;j++){
					try{
						errorStats.append(vectorY_list.get(i).get(j).toString());
						errorStats.append(",");
						errorStats.append(distance_list.get(i).get(j).toString());
						errorStats.append(",");
					} catch (NullPointerException e){
						errorStats.append("NA,");
						errorStats.append("NA,");
					}
				}
				errorStats.append(revisitTime_list.get(i).toString());
				errorStats.append(",");
				errorStats.append(time_list.get(i).toString());
				errorStats.append(",");
				errorStats.append(focalX_list.get(i).toString());
				errorStats.append(",");
				errorStats.append(focalY_list.get(i).toString());
				errorStats.append(",");
				errorStats.append(iid_list.get(i).toString());
				errorStats.newLine();
			}

			//flush and close writer
			errorStats.flush();
			errorStats.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


	}

	private static void inputData(){

		//Load original file first
		File file = new File (original_shapefile);

		FeatureCollection points=null;

		try {
			FileDataStore store;
			store = FileDataStoreFinder.getDataStore(file);
			SimpleFeatureSource featureSource = store.getFeatureSource();

			points = featureSource.getFeatures();

		} catch (IOException e) {
			e.printStackTrace();
		}

		original =  new ArrayList(Arrays.asList(points.toArray()));
		//original =  Arrays.asList(points.toArray()).listIterator();


		//load interpolated file second
		File file_interp = new File (original_shapefile_intepolated);

		FeatureCollection points_interp=null; 

		try {
			FileDataStore store_interp;
			store_interp = FileDataStoreFinder.getDataStore(file_interp);
			SimpleFeatureSource featureSource_interp = store_interp.getFeatureSource();

			points_interp = featureSource_interp.getFeatures();
			//points_interp.sort(SortBy.NATURAL_ORDER);

		} catch (IOException e) {
			e.printStackTrace();
		}

		original_interpolated = points_interp.features();

	}

	private static void exportShapefile(){

		/* Get an output file name and create the new shapefile
		 */

		try{
			
			String file_name = output_shapefile.toString();
			if (!file_name.endsWith(".shp"))
			    file_name += ".shp";
			
			File newFile = new File(file_name);

			ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

			Map<String, Serializable> params = new HashMap<String, Serializable>();
			params.put("url", newFile.toURI().toURL());
			params.put("create spatial index", Boolean.TRUE);

			ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);

			/*
			 * TYPE is used as a template to describe the file contents
			 */
			newDataStore.createSchema(((SimpleFeature) original.get(0)).getFeatureType());
			//newDataStore.createSchema(((SimpleFeature) previous_sf).getFeatureType());

			/*
			 * Write the features to the shapefile
			 */
			Transaction transaction = new DefaultTransaction("create");

			String typeName = newDataStore.getTypeNames()[0];
			SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);
			SimpleFeatureType SHAPE_TYPE = featureSource.getSchema();
			/*
			 * The Shapefile format has a couple limitations:
			 * - "the_geom" is always first, and used for the geometry attribute name
			 * - "the_geom" must be of type Point, MultiPoint, MuiltiLineString, MultiPolygon
			 * - Attribute names are limited in length 
			 * - Not all data types are supported (example Timestamp represented as Date)
			 * 
			 * Each data store has different limitations so check the resulting SimpleFeatureType.
			 */
			System.out.println("SHAPE:"+SHAPE_TYPE);

			if (featureSource instanceof SimpleFeatureStore) {
				SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
				/*
				 * SimpleFeatureStore has a method to add features from a
				 * SimpleFeatureCollection object, so we use the ListFeatureCollection
				 * class to wrap our list of features.
				 */
				//SimpleFeatureCollection collection = new ListFeatureCollection(original.get(0).getFeatureType(), original);
				//SimpleFeatureCollection collection = new ListFeatureCollection(((SimpleFeature) original.get(0)).getFeatureType(),Lists.newArrayList(original));
				SimpleFeatureCollection collection = new ListFeatureCollection(((SimpleFeature) original.get(0)).getFeatureType(),original);
				//SimpleFeatureCollection collection = new ListFeatureCollection(((SimpleFeature) previous_sf).getFeatureType(),groupies);
				featureStore.setTransaction(transaction);
				featureStore.addFeatures(collection);
				transaction.commit();
				transaction.rollback();
				transaction.close();
				System.exit(0); // success!
			} else {
				System.out.println(typeName + " does not support read/write access");
				System.exit(1);
			}

		}catch(IOException e){
			e.printStackTrace();
		}

	}

}
