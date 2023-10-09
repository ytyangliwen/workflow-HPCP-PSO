package cloud.workflowScheduling;

import java.io.*;
import java.math.*;
import java.util.*;

import org.apache.commons.math3.stat.*;

import cloud.workflowScheduling.methods.*;
import cloud.workflowScheduling.setting.*;
import cloud.workflowScheduling.test.HGSA;
import cloud.workflowScheduling.test.PCPandPSO2_4plus;


/*
 * 没有FILE_INDEX_MAX, 重复执行10次
 * Please download the DAX workflow archive from 
 * https://download.pegasus.isi.edu/misc/SyntheticWorkflows.tar.gz, 
 * unzip it and keep the DAX workflows in an appropriate position before running.
 */
public class EvaluateYLW {
	//because in Java float numbers can not be precisely stored, a very small number E is added before testing whether deadline is met
	public static final double E = 0.0000001; 
	
	//deadline factor.    tight, 0.005:0.005:0.05; loose, 0.05:0.05:0.5
	private static final double DF_START = 0.005, DF_INCR = 0.005, DF_END=0.05;	
//	private static final double DF_START = 0.02, DF_INCR = 0.005, DF_END=0.02;
	private static final int 	REPEATED_TIMES = 10;
	private static final int[] SIZES = {50, 100, 1000};//50, 100, 1000 task num of workflow
	//new ICPCP(), new PSO(), new ProLiS(1.5), new LACO(), new ACS1_8(), new TS_ACS(), new TS_MapOrder()
	//new ProLiS(1), new ProLiS(1.5), new ProLiS(2), new ProLiS(4), new ProLiS(8), new ProLiS(Double.MAX_VALUE)
	//new PCPandPSO1(), new PCPandPSO2() new ICPCP2_1(),new PCPandPSO2_2plus(), new HGSA()
//	new ICPCP(), new PSO(), new HGSA(), 
	private static final Scheduler[] METHODS = {new ICPCP(), new PSO(), new HGSA(), new PCPandPSO2_4plus()}; 
	//"GENOME", "CYBERSHAKE", "LIGO", "MONTAGE"    floodplain是两个两个并行，最后一个输出
	private static final String[] WORKFLOWS = {"CyberShake", "Epigenomics", "LIGO", "Montage",}; //{"floodplain"}; //{"CyberShake", "Epigenomics", "LIGO", "Montage", "Inspiral", "Sipht"};
	
	static final String WORKFLOW_LOCATION = "E:\\0Work\\workspace-notUTF-8\\L-ACO-paper8\\dax";
	static final String OUTPUT_LOCATION = "E:\\0Work\\workspace-notUTF-8\\L-ACO-paper8\\result";
	static final String ExcelFilePath = "E:\\0Work\\workspace-notUTF-8\\L-ACO-paper8\\result\\solution.xls";
	public static ExcelManage em;
	public static String sheetName;
	public static boolean isPrintExcel = false; //true false
	public static boolean onlyCalCostOfFeasible = false; //是否仅仅计算可行解的cost，之前跑的都是false
	
	public static void main(String[] args)throws Exception{
		
		if(isPrintExcel)
			ExcelManage.clearExecl(ExcelFilePath);
		int deadlineNum = (int)((DF_END-DF_START)/DF_INCR + 1);
		for(String workflow : WORKFLOWS){
			//three dimensions of these two arrays correspond to deadlines, methods, files, respectively
			double[][][] successResult = new double[deadlineNum][METHODS.length][REPEATED_TIMES * SIZES.length];
			double[][][] NCResult = new double[deadlineNum][METHODS.length][REPEATED_TIMES * SIZES.length]; 
			double[] refValues = new double[4];		//store cost and time of fastSchedule and cheapSchedule
			double[][][] usedVMNum = new double[deadlineNum][METHODS.length][REPEATED_TIMES * SIZES.length]; //输出租用的VM数量
			
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
			for(Scheduler s:METHODS)
				bw.write(s.getClass().getSimpleName()+"\t");
			bw.write("\r\n\r\n");
			printTo(bw, successResult, "success ratio");
			if(onlyCalCostOfFeasible)
				printCostOfFeasibleTo(bw, successResult, NCResult, "normalized cost"); //计算REPEATED_TIMES中某些次可行解的平均cost
			else
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
		for(int mi=0;mi<METHODS.length;mi++){		//method index
			Workflow wf1 = new Workflow(file);
			Scheduler method = METHODS[mi];
			wf1.setDeadline(deadline);	
			wf1.setDeadlineFactor(deadlineFactor); //为HGSA增加
			System.out.println("运行算法The current algorithm: " + method.getClass().getCanonicalName());

			//调用算法
			long starTime = System.currentTimeMillis();
			Solution sol = method.schedule(wf1);
			long endTime = System.currentTimeMillis();
			double runTime = (double)(endTime - starTime);
			
			String methodName = method.getClass().getName().substring(33);
			if(isPrintExcel)
				em.writeToExcel(ExcelFilePath, sheetName, deadlineFactor, deadline, methodName, mi, sol);
			
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
			
			successResult[di][mi][fi + si*REPEATED_TIMES] += isSatisfied;
			if(onlyCalCostOfFeasible) {
				if(isSatisfied == 1)
					NCResult[di][mi][fi + si*REPEATED_TIMES] += sol.calcCost() / benSched.getCheapSchedule().calcCost();
				else
					NCResult[di][mi][fi + si*REPEATED_TIMES] += 0;
			}
			else
				NCResult[di][mi][fi + si*REPEATED_TIMES] += sol.calcCost() / benSched.getCheapSchedule().calcCost();
			usedVMNum[di][mi][fi + si*REPEATED_TIMES] += sol.size();
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
	private static void printCostOfFeasibleTo(BufferedWriter bw, double[][][] successResult, double[][][] NCResult, String resultName)throws Exception{
		bw.write(resultName + "\r\n");
		for(int di = 0;di<=(DF_END-DF_START)/DF_INCR;di++){
			String text = df.format(DF_START + DF_INCR * di) + "\t";
			for(int mi=0;mi<METHODS.length;mi++) {
				text += df.format(StatUtils.sum(NCResult[di][mi])/StatUtils.sum(successResult[di][mi])) + "\t";
			}
			bw.write(text + "\r\n");
			bw.flush();
		}
		bw.write("\r\n\r\n\r\n");
	}
}