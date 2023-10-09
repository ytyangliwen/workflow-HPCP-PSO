package cloud.workflowScheduling.test;

import java.util.*;

import cloud.workflowScheduling.methods.Scheduler;
import cloud.workflowScheduling.setting.*;

public class HGSA implements Scheduler {

	private static final int POPSIZE = 200;
	private static final int NO_OF_ITE = 500;
	//parameters for HGSA are from the paper
	private static final double G0 = 5, alpha = 0, beta = 50, gamma = 0.3, delta = 0.1, epsilon = 10;		
	double G = 0;
	
	private Workflow wf;
	private int range;
	private Random rnd = new Random();
	
	private int dimension;	//number of tasks
	private VM[] vmPool;
	
	@Override
	public Solution schedule(Workflow wf) {
		this.wf = wf;
		
//		//工作流排序用pUpwardRank
//		this.wf.calcPURank(1.5);
//		Collections.sort(this.wf, new Task.PURankComparator()); 	
//		Collections.reverse(this.wf);	//sort based on pURank, larger first
		
		this.dimension = wf.size();
		this.range = wf.getMaxParallel() * VM.TYPE_NO;
		this.vmPool = new VM[range];
		for(int i = 0; i < vmPool.length; i++){
			vmPool[i] = new VM(i/wf.getMaxParallel()); // in vmPool, VMType ascends
		}
		
		double xMin = 0,  xMax = range - 1;	//boundary
		double[] globalBestPos = new double[dimension];	//global Best Position
		double globalBestFit = -1;
		Solution globalBestSol = null;	
		double[] globalWorstPos = new double[dimension];	//global Best Position
		double globalWorstFit = -1;
		Solution globalWorstSol = null;	
		
		Particle[] particles = new Particle[POPSIZE];
		//HEFT生成的解
		HEFT heft = new HEFT(wf, vmPool);
		double[] heftSol = heft.run();
		
		for (int i = 0; i < POPSIZE; i++){		//initialize particles 
			if(i == 0) {
				particles[i] = new Particle(xMin, xMax);
				for (int j = 0; j < dimension; j++){
					particles[i].position[j] = heftSol[j]; 
					particles[i].speed[j] = 0;			
				}
			}
			else
				particles[i] = new Particle(xMin, xMax);
			
			particles[i].generateSolution();
			particles[i].calFit();
			
			if (globalBestSol == null || globalWorstSol == null) {
				for (int j = 0; j < dimension; j++) {
					globalBestPos[j] = particles[i].position[j];
					globalWorstPos[j] = particles[i].position[j];
				}
				globalBestSol= particles[i].sol;
				globalWorstSol= particles[i].sol;
			}
			else if (particles[i].sol.isBetterThan(globalBestSol, wf.getDeadline())) {
				for (int j = 0; j < dimension; j++) {
					globalBestPos[j] = particles[i].position[j];
				}
				globalBestSol= particles[i].sol;	// 这里不需要clone，因为particle的sol每次迭代时都会重新new的
			}
			else if (globalWorstSol.isBetterThan(particles[i].sol, wf.getDeadline())) {
				for (int j = 0; j < dimension; j++) {
					globalWorstPos[j] = particles[i].position[j];
				}
				globalWorstSol= particles[i].sol;
			}
		}
//		System.out.println("the best initial solution:"+globalBestSol.calcCost()+";\t"+globalBestSol.calcMakespan());
		
		for (int iteIndex = 0; iteIndex < NO_OF_ITE; iteIndex++) {
			//计算G
			G = G0 * Math.pow(NO_OF_ITE/(double)iteIndex,gamma);
			
			//计算每个个体的适值, 找到最好和最差的的个体
			double bestFit = -1, worstFit = -1;
			for (int i = 0; i < POPSIZE; i++) {
				particles[i].calFit();
				
				if (particles[i].sol.isBetterThan(globalBestSol, wf.getDeadline())) {
					for (int j = 0; j < dimension; j++) {
						globalBestPos[j] = particles[i].position[j];
					}
					globalBestSol= particles[i].sol;	// 这里不需要clone，因为particle的sol每次迭代时都会重新new的
				}
				else if (globalWorstSol.isBetterThan(particles[i].sol, wf.getDeadline())) {
					for (int j = 0; j < dimension; j++) {
						globalWorstPos[j] = particles[i].position[j];
					}
					globalWorstSol= particles[i].sol;
				}
			}
			bestFit = 1/(1+globalBestSol.calcCost());
			worstFit = 1/(1+globalWorstSol.calcCost());
			
			//计算每个个体的M
			double[] rand = new double[POPSIZE];
			for (int i = 0; i < POPSIZE; i++) {
				particles[i].M = (particles[i].fit - worstFit)/(worstFit - bestFit);
				//顺便将i每个维度上的F置为0
				for (int d = 0; d < dimension; d++) 
					particles[i].F[d] = 0;
				//顺便产生popSize个随机数
				rand[i] = rnd.nextDouble();
			}
			
			//更新每个个体的位置和速度
			//计算每个个体的F
			for (int i = 0; i < POPSIZE; i++) {
				for (int j = 0; j < POPSIZE; j++) {
					if(i == j)
						continue;
					else {
						//计算两个个体之间的欧式距离
						double Rij = 0;
						for (int d = 0; d < dimension; d++) {
							Rij += Math.pow(particles[i].position[d] - particles[j].position[d], 2);
						}
						Rij = Math.pow(Rij, 0.5);
						
						//计算个体j在每个维度上给个体i的F
						for (int d = 0; d < dimension; d++) {
							particles[i].F[d] += rand[j] * (G * (particles[i].M * particles[j].M)/(Rij + epsilon) * 
									(particles[j].position[d]-particles[i].position[d]));
						}
					}
				}
			}
			//更新位置速度
			for (int i = 0; i < POPSIZE; i++) {
				for (int j = 0; j < dimension; j++) {
					particles[i].speed[j] = rand[i] * particles[i].speed[j] + particles[i].F[j]/particles[i].M;
					particles[i].position[j] = particles[i].position[j] + particles[i].speed[j];
				}
				
				//劣解阈值
				if(particles[i].M < delta) {
					for (int j = 0; j < dimension; j++) 
						particles[i].position[j] = globalWorstPos[j];
					int pos = (int)(rnd.nextDouble() * dimension);
					particles[i].position[pos] = rnd.nextDouble() * (xMax - xMin) + xMin;
				}
				
				particles[i].generateSolution();
			}
		}
		return globalBestSol;
	}
	
	private class Particle{
		private double[] position = new double[dimension];
		private double[] speed = new double[dimension];
		private Solution sol;
		public double fit = -1;
		public double M = -1;
		public double[] F = new double[dimension];
		
		//initialize a particle
		public Particle(double xMin, double xMax){
			for (int i = 0; i < dimension; i++){
				this.position[i] = rnd.nextDouble() * (xMax - xMin) + xMin; 
				this.speed[i] = 0;			
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
		}
		
		public double calFit() {
			fit = 1/(1+this.sol.calcCost());
			return fit;
		}

		public String toString() {
			if(sol != null)
				return "Particle [" + sol.calcCost()+ ", " + sol.calcMakespan()+ "]";
			return "";
		}
	}
}