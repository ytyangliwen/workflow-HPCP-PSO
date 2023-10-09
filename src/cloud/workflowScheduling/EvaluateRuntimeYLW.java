package cloud.workflowScheduling;

import java.io.*;

import cloud.workflowScheduling.methods.*;
import cloud.workflowScheduling.setting.*;
import cloud.workflowScheduling.test.*;

/**
 * 计算相同size下，每种算法在不同类型的工作流上的平均执行时间
 * @author Wen
 *
 */
public class EvaluateRuntimeYLW {
	private static final double DEADLINE_FACTOR = 0.05;//0.5; 
	private static final int REPEATED_TIMES = 3;  //10
	private static final String[] WORKFLOWS = {"CyberShake", "Epigenomics", "LIGO", "Montage", }; //"CyberShake", "Epigenomics", "LIGO", "Montage"

	//new PSO(), new PSO4(),new HGSA(),new PCPandPSO2_4plus(),
	private static final Scheduler[] METHODS = {new PSO(), new HGSA(),new PCPandPSO2_4plus(),}; //new ICPCP(), new PSO(), new ProLiS(1.5), new LACO() 
	private static final int[] SIZES = {50, 100, 1000}; //30, 50, 100, 1000
	
	public static void main(String[] args) throws Exception {
		long[][] runtime = new long[SIZES.length][METHODS.length];
		
		for(int fileSizeIndex = 0; fileSizeIndex<SIZES.length; fileSizeIndex++){
			int size = SIZES[fileSizeIndex];
			for(int methodIndex = 0; methodIndex < METHODS.length; methodIndex++){
				System.out.println("The current algorithm: " + METHODS[methodIndex].getClass().getCanonicalName());
				for(int typeIndex = 0;typeIndex<WORKFLOWS.length;typeIndex++){
					String workflow = WORKFLOWS[typeIndex];
					for(int i = 0;i<REPEATED_TIMES;i++){
						String file = EvaluateYLW.WORKFLOW_LOCATION + "\\" + workflow + "_" + size + ".xml";

						Workflow wf = new Workflow(file);	
						Benchmarks benSched = new Benchmarks(wf);
						double deadline = benSched.getFastSchedule().calcMakespan() + (benSched.getCheapSchedule().calcMakespan()
								- benSched.getFastSchedule().calcMakespan())* DEADLINE_FACTOR;
						wf.setDeadline(deadline);	
						
						long t1 = System.currentTimeMillis();
						METHODS[methodIndex].schedule(wf);
						runtime[fileSizeIndex][methodIndex] += System.currentTimeMillis() - t1;
					}
				}
				runtime[fileSizeIndex][methodIndex] /= WORKFLOWS.length * REPEATED_TIMES;
			}
		}

		
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(EvaluateYLW.OUTPUT_LOCATION + "\\runtime.txt")));
		for(int fileSizeIndex = 0; fileSizeIndex<SIZES.length; fileSizeIndex++){
			int size = SIZES[fileSizeIndex];
			bw.write(size +"\t");
			for(int methodIndex = 0; methodIndex < METHODS.length; methodIndex++)
				bw.write(runtime[fileSizeIndex][methodIndex]+"\t"); 
			bw.write("\r\n");
		}
		bw.flush();
		bw.close();
	}
}


