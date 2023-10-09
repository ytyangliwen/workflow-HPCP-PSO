package cloud.workflowScheduling;

import java.io.*;
import java.math.*;
import java.util.*;

import org.apache.commons.math3.stat.*;

import cloud.workflowScheduling.methods.*;
import cloud.workflowScheduling.setting.*;
import cloud.workflowScheduling.test.PCPandPSO2_4plus1;


/*
 * 没有FILE_INDEX_MAX, 重复执行10次
 * Please download the DAX workflow archive from 
 * https://download.pegasus.isi.edu/misc/SyntheticWorkflows.tar.gz, 
 * unzip it and keep the DAX workflows in an appropriate position before running.
 */
public class EvaluateYLWforPSOPar {
	//because in Java float numbers can not be precisely stored, a very small number E is added before testing whether deadline is met
	public static final double E = 0.0000001; 
	
	//deadline factor.    tight, 0.005:0.005:0.05; loose, 0.05:0.05:0.5
	private static final double DF_START = 0.005, DF_INCR = 0.005, DF_END=0.05;	
//	private static final double DF_START = 0.05, DF_INCR = 0.005, DF_END=0.05;
	private static final int 	REPEATED_TIMES = 2;
	private static final int[] SIZES = {50, 100, 1000};//50, 100, 1000 task num of workflow
	//new ICPCP(), new PSO(), new ProLiS(1.5), new LACO(), new ACS1_8(), new TS_ACS(), new TS_MapOrder()
	//new ProLiS(1), new ProLiS(1.5), new ProLiS(2), new ProLiS(4), new ProLiS(8), new ProLiS(Double.MAX_VALUE)
	//new PCPandPSO1(), new PCPandPSO2() new ICPCP2_1(),new PCPandPSO2_2plus(), new HGSA()
//	new ICPCP(), new PSO(), new HGSA(), 
//	private static final Scheduler[] METHODS = {new PCPandPSO2_4plus1()}; 
	//"GENOME", "CYBERSHAKE", "LIGO", "MONTAGE"    floodplain是两个两个并行，最后一个输出
	private static final String[] WORKFLOWS = {"CyberShake", "Epigenomics", "LIGO", "Montage"}; //{"floodplain"}; //{"CyberShake", "Epigenomics", "LIGO", "Montage", "Inspiral", "Sipht"};
	
	static final String WORKFLOW_LOCATION = "E:\\0Work\\workspace-notUTF-8\\L-ACO-paper8\\dax";
	static final String OUTPUT_LOCATION = "E:\\0Work\\workspace-notUTF-8\\L-ACO-paper8\\result";
	static final String ExcelFilePath = "E:\\0Work\\workspace-notUTF-8\\L-ACO-paper8\\result\\solution.xls";
	public static ExcelManage em;
	public static String sheetName;
	public static boolean isPrintExcel = false; //true false
	//跑PSO参数选择实验
	public static ArrayList<List<Double>> psoParameter = new ArrayList<List<Double>>(); 	
	
	public static void main(String[] args)throws Exception{
		psoParameter.add(Arrays.asList(50.0, 	0.2, 	1.6)); //1
		psoParameter.add(Arrays.asList(50.0,	0.5,	1.8)); //2
		psoParameter.add(Arrays.asList(50.0,	0.8,	2.0));//3
		psoParameter.add(Arrays.asList(50.0,	1.1,	2.2));//4
		psoParameter.add(Arrays.asList(100.0,	0.2,	1.8));//5
		psoParameter.add(Arrays.asList(100.0,	0.5,	1.6));//6
		psoParameter.add(Arrays.asList(100.0,	0.8,	2.2));//7
		psoParameter.add(Arrays.asList(100.0,	1.1,	2.0));//8
		psoParameter.add(Arrays.asList(150.0,	0.2,	2.0));//9
		psoParameter.add(Arrays.asList(150.0,	0.5,	2.2));//10
		psoParameter.add(Arrays.asList(150.0,	0.8,	1.6));//11
		psoParameter.add(Arrays.asList(150.0,	1.1,	1.8));//12
		psoParameter.add(Arrays.asList(200.0,	0.2,	2.2));//13
		psoParameter.add(Arrays.asList(200.0,	0.5,	2.0));//14
		psoParameter.add(Arrays.asList(200.0,	0.8,	1.8));//15
		psoParameter.add(Arrays.asList(200.0,	1.1,	1.6));//16
		
		if(isPrintExcel)
			ExcelManage.clearExecl(ExcelFilePath);
		int deadlineNum = (int)((DF_END-DF_START)/DF_INCR + 1);
		
		for(String workflow : WORKFLOWS){
			//three dimensions of these two arrays correspond to deadlines, methods, files, respectively
			double[][][] successResult = new double[deadlineNum][psoParameter.size()][REPEATED_TIMES * SIZES.length];
			double[][][] NCResult = new double[deadlineNum][psoParameter.size()][REPEATED_TIMES * SIZES.length]; 
			double[] refValues = new double[4];		//store cost and time of fastSchedule and cheapSchedule
			double[][][] usedVMNum = new double[deadlineNum][psoParameter.size()][REPEATED_TIMES * SIZES.length]; //输出租用的VM数量
			
			for(int di = 0; di<=(DF_END-DF_START)/DF_INCR; di++){	// deadline index
				for(int si = 0; si <SIZES.length; si++){			// size index
					int size = SIZES[si];
					sheetName = workflow + "_" + size;
					if(isPrintExcel)
						em = ExcelManage.initExecl(ExcelFilePath, sheetName);
					for(int timeI = 0;timeI<REPEATED_TIMES;timeI++){			//workflow file index
						String file = WORKFLOW_LOCATION + "\\" + workflow + "_" + size + ".xml";
//						String file = WORKFLOW_LOCATION + "\\" + workflow + ".xml";
						test(file, di, timeI, si, successResult, NCResult, refValues, usedVMNum);
					}
				}
			}
			BufferedWriter bw = new BufferedWriter(new FileWriter(OUTPUT_LOCATION + "\\" + workflow + ".txt"));
			bw.write("used methods: ");
			for(int parIndex=0; parIndex < psoParameter.size(); parIndex++)
				bw.write(parIndex+"\t");
			bw.write("\r\n\r\n");
			printTo(bw, successResult, "success ratio");
			printTo(bw, NCResult, "normalized cost");
			printTo(bw, usedVMNum, "leased VM number");

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
	 */
	private static void test(String file, int di, int fi, int si, double[][][] successResult,
			double[][][] NCResult, double[] refValues, double[][][] usedVMNum){
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
		System.out.println("deadlineFactor=" + String.format("%.3f", deadlineFactor) + ", deadline = " + String.format("%.3f", deadline));

		System.out.println();
//		for(int mi=0;mi<METHODS.length;mi++){		//method index
		for(int parIndex=0; parIndex < psoParameter.size(); parIndex++) {
			Workflow wf1 = new Workflow(file);
			Scheduler method = new PCPandPSO2_4plus1(psoParameter.get(parIndex));
			wf1.setDeadline(deadline);	
			wf1.setDeadlineFactor(deadlineFactor); //为HGSA增加
			System.out.println("运行算法The current algorithm: " + method.getClass().getCanonicalName());

			//调用算法
			long starTime = System.currentTimeMillis();
			Solution sol = method.schedule(wf1);
			long endTime = System.currentTimeMillis();
			double runTime = (double)(endTime - starTime);
			
			if(sol == null) {
				System.out.println("solution is " + sol + "!\r\n");
				continue;
			}
			int isSatisfied = sol.calcMakespan()<=deadline + E ? 1 : 0;
			List<Integer> result = sol.validateId(wf1);
			if(result.get(0).intValue() == 0) {
				if(isPrintExcel)
					em.writeToExcel(ExcelFilePath, sheetName, result.get(1).intValue(), result.get(0).intValue());
				throw new RuntimeException();
			}
			System.out.println("runtime：" + runTime + "ms;   solution: " + sol);
			
			successResult[di][parIndex][fi + si*REPEATED_TIMES] += isSatisfied;
			NCResult[di][parIndex][fi + si*REPEATED_TIMES] += sol.calcCost() / benSched.getCheapSchedule().calcCost();
			usedVMNum[di][parIndex][fi + si*REPEATED_TIMES] += sol.size();
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
			for(int mi=0;mi<psoParameter.size();mi++)
				text += df.format(StatUtils.mean(result[di][mi])) + "\t";
			bw.write(text + "\r\n");
			bw.flush();
		}
		bw.write("\r\n\r\n\r\n");
	}
}