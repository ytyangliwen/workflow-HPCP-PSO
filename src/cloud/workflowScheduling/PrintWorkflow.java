package cloud.workflowScheduling;

import java.io.*;
import java.math.*;
import org.apache.commons.math3.stat.*;

import cloud.workflowScheduling.methods.*;
import cloud.workflowScheduling.setting.*;
/*
 * 没有FILE_INDEX_MAX, 重复执行10次
 * Please download the DAX workflow archive from 
 * https://download.pegasus.isi.edu/misc/SyntheticWorkflows.tar.gz, 
 * unzip it and keep the DAX workflows in an appropriate position before running.
 */
public class PrintWorkflow {
	//because in Java float numbers can not be precisely stored, a very small number E is added before testing whether deadline is met
	public static final double E = 0.0000001; 
	
	//deadline factor.    tight, 0.005:0.005:0.05; loose, 0.05:0.05:0.5
	private static final double DF_START = 0.005, DF_INCR = 0.005, DF_END=0.05;	
	private static final int 	REPEATED_TIMES = 1;
	private static final int[] SIZES = {50, 100};//50, 100, 1000 task num of workflow
	//new ICPCP(), new PSO(), new ProLiS(1.5), new LACO(), new ACS1_8(), new TS_ACS(), new TS_MapOrder()
	//new ProLiS(1), new ProLiS(1.5), new ProLiS(2), new ProLiS(4), new ProLiS(8), new ProLiS(Double.MAX_VALUE)
	private static final Scheduler[] METHODS = {new ProLiS(1.5)}; 
	//"GENOME", "CYBERSHAKE", "LIGO", "MONTAGE"    floodplain是两个两个并行，最后一个输出
	private static final String[] WORKFLOWS = {"CyberShake", "Epigenomics", "LIGO", "Montage"}; //"CyberShake", "Epigenomics", "LIGO", "Montage"   "floodplain"
	
	static final String WORKFLOW_LOCATION = "E:\\0Work\\workspace-notUTF-8\\L-ACO-paper\\dax";
	static final String OUTPUT_LOCATION = "E:\\0Work\\workspace-notUTF-8\\L-ACO-paper\\result";
	
	public static void main(String[] args)throws Exception{
		int deadlineNum = (int)((DF_END-DF_START)/DF_INCR + 1);
		
		for(String workflow : WORKFLOWS){
			//three dimensions of these two arrays correspond to deadlines, methods, files, respectively
			double[][][] successResult = new double[deadlineNum][METHODS.length][REPEATED_TIMES * SIZES.length];
			double[][][] NCResult = new double[deadlineNum][METHODS.length][REPEATED_TIMES * SIZES.length]; 
			double[] refValues = new double[4];		//store cost and time of fastSchedule and cheapSchedule

			for(int si = 0; si <SIZES.length; si++){			// size index
				int size = SIZES[si];
				for(int timeI = 0;timeI<REPEATED_TIMES;timeI++){			//workflow file index
					String file = WORKFLOW_LOCATION + "\\" + workflow + "_" + size + ".xml";
//					String file = WORKFLOW_LOCATION + "\\" + workflow + ".xml";
					test(file, 0, timeI, si, successResult, NCResult, refValues, workflow);
				}
			}
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(OUTPUT_LOCATION + "\\" + workflow + ".txt"));
			bw.write("used methods: ");
			for(Scheduler s:METHODS)
				bw.write(s.getClass().getSimpleName()+"\t");
			bw.write("\r\n\r\n");
			printTo(bw, successResult, "success ratio");
			printTo(bw, NCResult, "normalized cost");

			bw.write("reference values (CF, MF, CC, MC)\r\n");
			double divider = SIZES.length * REPEATED_TIMES * deadlineNum;
			for(double refValue : refValues)
				bw.write(refValue / divider + "\t");
			bw.close();	
		}
	}
	
	/**
	 * 计算file工作流(在第si个size下，fileIndex下)在deadline为di松紧下时各算法的结果（成功率，标准cost）
	 * @param file xml所在的文件
	 * @param di 第di个deadline间隔
	 * @param fi 重复执行n次，这是第fi次
	 * @param si task size
	 * @param successResult 在di下个算法的成功率
	 * @param NCResult normalized cost
	 * @param refValues 
	 * @throws IOException 
	 */
	private static void test(String file, int di, int fi, int si, double[][][] successResult,
			double[][][] NCResult, double[] refValues, String workflowName) throws IOException{
		//解析file文件中的工作流
		Workflow wf = new Workflow(file);	
		
		Benchmarks benSched = new Benchmarks(wf); //获得当前工作流的两个Benchmark解，为了计算max min的deadline
		System.out.print("Benchmark-FastSchedule：" + benSched.getFastSchedule());
		System.out.print("Benchmark-CheapSchedule：" + benSched.getCheapSchedule());
		System.out.print("Benchmark-MinCost8Schedule：" + benSched.getMinCost8Schedule());
		
		//求当前的deadline = min+ (max-min)*deadlineFactor
		double deadlineFactor = DF_START + DF_INCR * di; 
		double deadline = benSched.getFastSchedule().calcMakespan() + (benSched.getCheapSchedule().calcMakespan()
				- benSched.getFastSchedule().calcMakespan())* deadlineFactor;

		for(int mi=0;mi<METHODS.length;mi++){		//method index
			Scheduler method = METHODS[mi];
			wf.setDeadline(deadline);	
			System.out.println("The current algorithm: " + method.getClass().getCanonicalName());

			//调用算法
			Solution sol = method.schedule(wf);
			
			/**
			 * 打印workflow时间
			 */
			int vmNum = 9; 
			double[] EC2Speed = {4400, 8800, 17600, 35200, 57200, 114400}; 
			double[] LACOSpeed = {1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5}; //工作流在L-ACO论文中云上的执行时间
			
			double[][] exeTimeOnVM1 = new double[wf.size()][6]; //工作流在Amason云上的执行时间
			double[][] exeTimeOnVM21 = new double[wf.size()][9]; //工作流在L-ACO论文中云上的执行时间*1000
			double[][] exeTimeOnVM2 = new double[wf.size()][9]; //工作流在L-ACO论文中云上的执行时间
			double[][] transTime = new double[wf.size()][wf.size()]; //task间的传输时间
			//计算执行时间
			for(int i= 0; i < wf.size(); i++){	
				Task task = wf.get(i);
				int taskId = task.getId();
				for(int j= 0; j < 6; j++) {
					exeTimeOnVM1[taskId][j] = task.getTaskSize()*1000/EC2Speed[j];
				}
				for(int j= 0; j < 9; j++) {
					exeTimeOnVM21[taskId][j] = task.getTaskSize()*1000/LACOSpeed[j];
					exeTimeOnVM2[taskId][j] = task.getTaskSize()/LACOSpeed[j];
				}
			}
			//计算传输时间
			for(int i= 0; i < wf.size(); i++){	
				Task task = wf.get(i);
				int taskId = task.getId();
				//初始化为0
				for(int j= 0; j < wf.size(); j++) {
					transTime[taskId][j] = 0;
				}
				
				for(Edge e : task.getOutEdges()){
					int childId = e.getDestination().getId();
					double dataSize = e.getDataSize();
					transTime[taskId][childId] = dataSize/VM.NETWORK_SPEED;
				}
			}
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(OUTPUT_LOCATION + "\\Time\\" + workflowName + SIZES[si] + "Time.txt"));
			java.text.DecimalFormat df = new java.text.DecimalFormat("0.000");
			{df.setRoundingMode(RoundingMode.HALF_UP); }
			String text;

			//打印exeTimeOnVM1
			text = "";
			bw.write("Amason云的执行时间：");
			bw.newLine();
			bw.write("\t\t");
			for(int i= 0; i < 6; i++){ //打印头
				bw.write(String.format("%-8d", i)+"\t");
			}
			bw.newLine();
			for(int i= 0; i < wf.size(); i++){
				text = i + "\t";
				for(int j= 0; j < 6; j++) {
					text += String.format("%-8s", df.format(exeTimeOnVM1[i][j]))+"\t";
				}
				bw.write(text + "\r\n");
				bw.flush();
			}
			bw.newLine();
			bw.newLine();
			bw.newLine();
			//打印exeTimeOnVM21
			text = "";
			bw.write("L-ACO云的执行时间*1000：");
			bw.newLine();
			bw.write("\t\t");
			for(int i= 0; i < 9; i++){ //打印头
				bw.write(String.format("%-12d", i)+"\t");
			}
			bw.newLine();
			for(int i= 0; i < wf.size(); i++){
				text = i + "\t";
				for(int j= 0; j < 9; j++) {
					text += String.format("%-12s", df.format(exeTimeOnVM21[i][j]))+"\t";
				}
				bw.write(text + "\r\n");
				bw.flush();
			}
			bw.newLine();
			bw.newLine();
			bw.newLine();
			//打印exeTimeOnVM2
			text = "";
			bw.write("L-ACO云的执行时间：");
			bw.newLine();
			bw.write("\t\t");
			for(int i= 0; i < 9; i++){ //打印头
				bw.write(String.format("%-8d", i)+"\t");
			}
			bw.newLine();
			for(int i= 0; i < wf.size(); i++){
				text = i + "\t";
				for(int j= 0; j < 9; j++) {
					text += String.format("%-8s", df.format(exeTimeOnVM2[i][j]))+"\t";
				}
				bw.write(text + "\r\n");
				bw.flush();
			}
			bw.newLine();
			bw.newLine();
			bw.newLine();
			//打印transTime
			text = "";
			bw.write("传输时间：");
			bw.newLine();
			bw.write("\t");
			for(int i= 0; i < wf.size(); i++){ //打印头
				bw.write(String.format("%4d", i)+"\t");
			}
			bw.newLine();
			for(int i= 0; i < wf.size(); i++){
				text = i + "\t";
				for(int j= 0; j < wf.size(); j++) {
					text += df.format(transTime[i][j])+"\t";
				}
				bw.write(text + "\r\n");
				bw.flush();
			}
			bw.newLine();
			bw.newLine();
			bw.newLine();
			bw.close();	
			
			
			
			if(sol == null)
				continue;
			int isSatisfied = sol.calcMakespan()<=deadline + E ? 1 : 0;
			if(sol.validate(wf) == false)
				throw new RuntimeException();
			System.out.println(sol);
			successResult[di][mi][fi + si*REPEATED_TIMES] += isSatisfied;
			NCResult[di][mi][fi + si*REPEATED_TIMES] += sol.calcCost() / benSched.getCheapSchedule().calcCost();
		}
		refValues[0]+=benSched.getFastSchedule().calcCost();
		refValues[1]+=benSched.getFastSchedule().calcMakespan();
		refValues[2]+=benSched.getCheapSchedule().calcCost();
		refValues[3]+=benSched.getCheapSchedule().calcMakespan();
	}
	
	private static final java.text.DecimalFormat df = new java.text.DecimalFormat("0.000");
	static {df.setRoundingMode(RoundingMode.HALF_UP); }
	private static void printTo(BufferedWriter bw, double[][][] result, String resultName)throws Exception{
		bw.write(resultName + "\r\n");
		for(int di = 0;di<=(DF_END-DF_START)/DF_INCR;di++){
			String text = df.format(DF_START + DF_INCR * di) + "\t";
			for(int mi=0;mi<METHODS.length;mi++)
				text += df.format(StatUtils.mean(result[di][mi])) + "\t";
			bw.write(text + "\r\n");
			bw.flush();
		}
		bw.write("\r\n\r\n\r\n");
	}
}