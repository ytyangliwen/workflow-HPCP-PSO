package cloud.workflowScheduling.test;

import java.util.*;

import cloud.workflowScheduling.methods.ICPCP;
import cloud.workflowScheduling.methods.Scheduler;
import cloud.workflowScheduling.setting.*;

/*重复交替执行混合框架和解编码策略的有效性：去掉解修复策略和PSO加ICPCP一个解混合的方法进行对比。这实现的是PSO和ICPCP混合的方法*/
public class HybridFrameworkAndEncoding implements Scheduler {

	private static final int POPSIZE = 100;
	private static final int NO_OF_ITE = 200;
	private static final double W = 0.5f, C1 = 2f, C2 = 2f;		//parameters for PSO are from the paper
	
	private Workflow wf;
	private int range;
	private Random rnd = new Random();
	
	private int dimension;	//number of tasks
	private VM[] vmPool;
	
	@Override
	public Solution schedule(Workflow wf) {
		this.wf = wf;
		
		this.dimension = wf.size();
		this.range = wf.getMaxParallel() * VM.TYPE_NO;
		this.vmPool = new VM[range];
		for(int i = 0; i < vmPool.length; i++){
			vmPool[i] = new VM(i/wf.getMaxParallel()); // in vmPool, VMType ascends
		}
		
		//获得IC-PCP求得的启发解
		Solution ICPCPSol = new ICPCP().schedule(this.wf);
		double[] ICPCPPosition = new double[dimension];
		if(ICPCPSol != null) {
		List<Integer> VmIds = new ArrayList<Integer>();
		int index = 0;
		for(Task t : this.wf) {
			for (Map.Entry<Task, Allocation> entry : ICPCPSol.getRevMapping().entrySet()) {
				if(entry.getKey().equals(t)) {
					VmIds.add(entry.getValue().getVM().getId());
					break;
				}
			}
			ICPCPPosition[index] = -1;
			index++;
		}
		boolean[] usedVMs = new boolean[range];
		for(int i = 0; i < range; i++) {
			usedVMs[i] =false;
		}
		index = 0;
		for(Task t : this.wf) {
			if(ICPCPPosition[index] == -1) {
				int trueVmId = -1;
				int PCPVmId = VmIds.get(index); //获取任务t在ICPCP解中对应的VMId
				int startIndex = ICPCPSol.getRevMapping().get(t).getVM().getType()*wf.getMaxParallel();
				for(int j = startIndex; j < startIndex+wf.getMaxParallel(); j++) { //找到任务t在PSO虚拟机池中对应的VMId
					if(usedVMs[j] == false) {
						trueVmId = j;
						break;
					}
				}
				ICPCPPosition[index] = trueVmId;
				if(trueVmId == -1)
					System.out.println("youwenti");
				usedVMs[trueVmId] = true;
				for(int j = index; j < this.dimension; j++) {
					if(PCPVmId == VmIds.get(j).intValue())
						ICPCPPosition[j] = trueVmId;
				}
			}
			index++;
		}
		}
		
		
		double xMin = 0,  xMax = range - 1;	//boundary
		double vMax = xMax;					//maximum velocity
		double[] globalBestPos = new double[dimension];	//global Best Position
		Solution globalBestSol = null;		
		
		Particle[] particles = new Particle[POPSIZE];
		for (int i = 0; i < POPSIZE; i++){		//initialize particles 
			particles[i] = new Particle(vMax, xMin, xMax);
			if(i == 0 && ICPCPSol != null) {
				for (int d = 0; d < dimension; d++){
					particles[i].position[d] = ICPCPPosition[d]; 
					particles[i].speed[d] = vMax * rnd.nextDouble() - vMax/2;			
					particles[i].bestPos[d] = particles[i].position[d];	
				}
			}
			particles[i].generateSolution();
			
			if (globalBestSol == null || particles[i].sol.isBetterThan(globalBestSol, wf.getDeadline())) {
				for (int j = 0; j < dimension; j++)
					globalBestPos[j] = particles[i].position[j];
				globalBestSol= particles[i].sol;	// 这里不需要clone，因为particle的sol每次迭代时都会重新new的
			}
		}
//		System.out.println("the best initial solution:"+globalBestSol.calcCost()+";\t"+globalBestSol.calcMakespan());
		
		for (int iteIndex = 0; iteIndex < NO_OF_ITE; iteIndex++) {
//			W = (double) (1.0 - iteIndex * 0.6 / 499);	//惯性递减，比w = 1效果要好一些。
			for (int i = 0; i < POPSIZE; i++) {
				for (int j = 0; j < dimension; j++) {
					particles[i].speed[j] = W * particles[i].speed[j]
					        + C1 * rnd.nextDouble() * (particles[i].bestPos[j] - particles[i].position[j])
							+ C2 * rnd.nextDouble() * (globalBestPos[j] - particles[i].position[j]);  //全局最好位置作为邻居
					particles[i].speed[j] = Math.min(particles[i].speed[j], vMax);
					
					particles[i].position[j] = particles[i].position[j] + particles[i].speed[j];

					particles[i].position[j] = Math.max(particles[i].position[j], xMin);	//bound
					particles[i].position[j] = Math.min(particles[i].position[j], xMax);
				}
				particles[i].generateSolution();
				//record a better solution
				if (globalBestSol == null || particles[i].sol.isBetterThan(globalBestSol, wf.getDeadline())) {
					for (int j = 0; j < dimension; j++)
						globalBestPos[j] = particles[i].position[j];
					globalBestSol= particles[i].sol;
//					System.out.printf("Iteration index：%3d\t%5.2f\t%5.2f\n",iteIndex,
//							globalBestSol.calcCost(),	globalBestSol.calcMakespan());
				}
			}
//			System.out.printf("Iteration index：%3d\t%5.2f\t%5.2f\n",iteIndex,
//					globalBestSol.calcCost(),	globalBestSol.calcMakespan());
		}
//		System.out.println("Globle best is :" + globalBestSol.calcCost()+";\t"+globalBestSol.calcMakespan());
		return globalBestSol;
	}
	
	private class Particle{
		private double[] position = new double[dimension];
		private double[] speed = new double[dimension];
		private double[] bestPos = new double[dimension];
		private Solution sol, bestSol = null;
		
		//initialize a particle
		public Particle(double vMax, double xMin, double xMax){
			for (int i = 0; i < dimension; i++){
				this.position[i] = rnd.nextDouble() * (xMax - xMin) + xMin; 
				this.speed[i] = vMax * rnd.nextDouble() - vMax/2;			
				this.bestPos[i] = this.position[i];	
			}
		}
		
		public void generateSolution() {		//generate solution from position
			this.sol = new Solution();	
			for(int i=0;i<position.length;i++){
				Task task = wf.get(i);		// tasks in wf is a topological sort
				int vmIndex = (int)(Math.floor(position[i])); //向下取整
				VM vm = vmPool[vmIndex];
				double startTime = sol.calcEST(task, vm);
				sol.addTaskToVM(vm, task, startTime, true);
			}
			
			//record the best solution this particle has found
			if (bestSol==null || this.sol.isBetterThan(bestSol, wf.getDeadline())){
				for (int j = 0; j < dimension; j++)
					this.bestPos[j] = this.position[j];	
				this.bestSol = this.sol;	
			}
		}

		public String toString() {
			if(sol != null)
				return "Particle [" + sol.calcCost()+ ", " + sol.calcMakespan()+ "]";
			return "";
		}
	}
}