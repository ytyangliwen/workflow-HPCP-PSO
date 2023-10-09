package cloud.workflowScheduling.test;

import java.io.IOException;
import java.util.*;

import cloud.workflowScheduling.*;
import cloud.workflowScheduling.methods.Scheduler;
import cloud.workflowScheduling.setting.*;


/*Abrishami, Saeid, Mahmoud Naghibzadeh, and Dick HJ Epema. "Deadline-constrained workflow scheduling algorithms 
  for Infrastructure as a Service Clouds." Future Generation Computer Systems 29.1 (2013): 158-169.*/
public class solConstructEffect implements Scheduler {
	
	private final double bestVMSpeed = VM.SPEEDS[VM.FASTEST];
	private Workflow wf;
	private Solution solution ;
	int num =0;
	double[] subDeadline;
//	LinkedList<VM> updatedVM = new LinkedList<VM>();
	boolean xiufu = true; //可行解是否修复
	HashMap<Task, Integer> mostCostEffectiveVmType = new HashMap<Task, Integer>();
	private VM[] vmPool;
	public Solution schedule(Workflow wf) { 
		num =0;
		this.wf = wf;
		
		//task的isAssigned置为false
		for(int i = 0; i < wf.size(); i++) {
			Task t = wf.get(i);
			t.setAssigned(false);
		}
		
		//计算每个task对应的最高性价比VM类型
		for(int i = 0; i < wf.size(); i++) {
			Task t = wf.get(i);
			double minCost = Double.MAX_VALUE; 
			int bestVmType = -1;
			double cost = 0.0;
			for(int j = 0; j < VM.TYPE_NO; j++) {
				//只有exe
//				cost = Math.ceil((t.getTaskSize()/VM.SPEEDS[j])/VM.INTERVAL)
//						* VM.UNIT_COSTS[j];
				//exe+trans
				double transTime = 0;
				for(Edge e : t.getOutEdges()) {
					transTime = Math.max(transTime, e.getDataSize());
				}
				cost = Math.ceil((t.getTaskSize()/VM.SPEEDS[j] + transTime/VM.NETWORK_SPEED)/VM.INTERVAL)
						* VM.UNIT_COSTS[j];
				if(cost < minCost) {
					minCost = cost;
					bestVmType = j;
				}
			}
			mostCostEffectiveVmType.put(t, new Integer(bestVmType));
		}
		
		//用LACO的方法确定task的子deadline
		if(xiufu) {
		wf.calcPURank(1.5);
		List<Task> tasks = new ArrayList<Task>(wf);
		Collections.sort(tasks, new Task.PURankComparator()); 	
		Collections.reverse(tasks);	//sort based on pURank, larger first
		subDeadline = new double[wf.size()];
		double CPLength = wf.get(0).getpURank(); 	//critical path
//		double CPLength = wf.get(0).getbLevel(); 	//critical path
		for(int i = 0; i < wf.size(); i++) {
			Task t = wf.get(i);
			subDeadline[i] = (CPLength - t.getpURank() + t.getTaskSize()/VM.SPEEDS[VM.FASTEST])
					/CPLength * wf.getDeadline();
//			subDeadline[i] = (CPLength - t.getbLevel() + t.getTaskSize()/VM.SPEEDS[VM.FASTEST])
//					/CPLength * wf.getDeadline();
		}
		}
		
//		System.out.println("工作流的最大并行度：" + wf.getMaxParallel());
		this.solution = new Solution();
		this.vmPool = new VM[wf.getMaxParallel() * VM.TYPE_NO];
		for(int i = 0; i < vmPool.length; i++){
			vmPool[i] = new VM(i/wf.getMaxParallel()); // in vmPool, VMType ascends
		}
		try{
			init();									// init
			assignParents(wf.get(wf.size() - 1));	// parent assign for exit task
	
			// allocate entry and exit tasks
			solution.addTaskToVM(getEarliestVM(), wf.get(0), 0, false);
			solution.addTaskToVM(getLatestVM(), wf.get(wf.size()-1), solution.calcMakespan(), true);
			
//			System.out.println("PCP的个数："+ num);
			return solution;
		}catch(RuntimeException e){
			//it means ICPCP fails to yield a solution meeting the deadline. This is because 'assignPath' may fail
			return null;
		}
	}
	
	private void init(){					//Algorithm 1 in the paper; for cases of initialization and update
		Task entryTask = wf.get(0);
		entryTask.setAST(0);
		entryTask.setAFT(0);
		entryTask.setAssigned(true);
		
		for(int i=1; i<wf.size(); i++){		// compute EST, EFT, critical parent via Eqs. 1 and 2; skip entry task
			Task task = wf.get(i);

			//此处EST定义不考虑resource的available time，且还要计算critical parent；所以没有使用solution.calcEST方法
			double EST = -1;
			double ESTForCritical = -1;
			Task criticalParent = null;		
			for(Edge e: task.getInEdges()){
				Task parent = e.getSource();
				double startTime = e.getDataSize()/VM.NETWORK_SPEED;
				//if assigned, use AFT; otherwise, use EFT
				startTime += parent.isAssigned() ? parent.getAFT() : parent.getEFT();
				EST = Math.max(EST, startTime);				//determine EST
				if(startTime > ESTForCritical && parent.isAssigned()==false){	//determine critical parent
					ESTForCritical = startTime;
					criticalParent = parent;
				}
			}
			if(task.isAssigned() == false){
				task.setEST(EST);
				task.setEFT(EST + task.getTaskSize() / bestVMSpeed);
			}
			//分配了的还需要更新critical parent:因为task a在assignParents，可能有两个parent b和c，所以必须要更新了
			task.setCriticalParent(criticalParent);	
		}

		Task exitTask = wf.get(wf.size()-1);	//Note, EST, EFT, critialParent of exitTask have been set above
		exitTask.setAFT(wf.getDeadline());
		exitTask.setAST(wf.getDeadline());
		exitTask.setAssigned(true);
		for(int j = wf.size() - 2; j>=0; j--){	// compute LFT via Eq. 3; reverse order, skip exit node
			Task task = wf.get(j);
			if(task.isAssigned())
				continue;
			
			double lft = Double.MAX_VALUE;
			for(Edge e : task.getOutEdges()){
				Task child = e.getDestination();
				double finishTime;
				if(child.isAssigned())	
					finishTime = child.getAST() - e.getDataSize() / VM.NETWORK_SPEED; //与论文中不同，论文中的有问题
				else
					finishTime = child.getLFT() - child.getTaskSize()/bestVMSpeed - e.getDataSize() / VM.NETWORK_SPEED;
				lft = Math.min(lft, finishTime);
			}
			task.setLFT(lft);
		}
	}
	
	private void assignParents(Task task){			//Algorithm 2 in the paper
		while(task.getCriticalParent() != null){	
			List<Task> PCP = new ArrayList<Task>();
			Task ti = task;
			while(ti.getCriticalParent() != null){		// while (there exists an unassigned parent of ti)
				PCP.add(0, ti.getCriticalParent());   	//add CriticalParent(ti) to the beginning of PCP
				ti = ti.getCriticalParent();
			}
			num++;
			
			if(xiufu) {
			//设置LFT=subDeadline
			for(int i = 1; i < wf.size()-1; i++) {
				Task t = wf.get(i);
				t.setLFT(subDeadline[i]);
			}
			}
			
//			System.out.println("部分关键路径的size：" + PCP.size());
			solution = assignPath(PCP, solution);	//path assign
			init();				//re-init, i.e., update in the paper
			for(Task tj : PCP)	//call AssignParents(ti)
				assignParents(tj);
		}
	}
	
	//choose the cheapest service for PCP; 从existing和new VM中一起寻找最便宜的；论文里的只要existing里找到就停止
	private Solution assignPath(List<Task> PCP, Solution lastS){
		int existingVmNum = lastS.size();
		VM.setInternalId(existingVmNum);
		
		//用PSO迭代
		PSOPCP pso = new PSOPCP(PCP, lastS, this.vmPool);
		lastS = pso.run();
		
		return lastS;
	}
	
	//search a time slot in vm between EST and LFT for task allocation
	//returning -1 means this task can not be placed to this vm between EST and LFT, in the target solution
	private double searchStartTime(Solution solution, VM vm, Task task, double EST, double LFT){
		LinkedList<Allocation> list = solution.get(vm);
		
		for(int i = 0;i<list.size()+1;i++){
			double timeSlotStart, timeSlotEnd;
			if(i == 0){ 
				timeSlotStart = 0;
				timeSlotEnd	= list.get(i).getStartTime();
			}else if(i==list.size()){
				timeSlotStart = list.get(i-1).getFinishTime();
				timeSlotEnd = Double.MAX_VALUE;
			}else{
				timeSlotStart = list.get(i-1).getFinishTime();
				timeSlotEnd	= list.get(i).getStartTime();
			}
			double slackTime = LFT - EST - task.getTaskSize()/vm.getSpeed();
			if(EST + slackTime >= timeSlotStart){			//condition1：startTime satisfies
				double startTime = Math.max(timeSlotStart, EST);
				//condition2：slot is large enough to support this task
				if(timeSlotEnd - startTime >= task.getTaskSize() / vm.getSpeed())	
					return startTime;
			}
		}
		return -1;
	}
	//与上边的区别，不需要满足LFT，只要能找到执行task的最早间隔即可
	private double searchStartTimeNoLFT(Solution solution, VM vm, Task task, double EST){
		LinkedList<Allocation> list = solution.get(vm);
		
		for(int i = 0;i<list.size()+1;i++){
			double timeSlotStart, timeSlotEnd;
			if(i == 0){ 
				timeSlotStart = 0;
				timeSlotEnd	= list.get(i).getStartTime();
			}else if(i==list.size()){
				timeSlotStart = list.get(i-1).getFinishTime();
				timeSlotEnd = Double.MAX_VALUE;
			}else{
				timeSlotStart = list.get(i-1).getFinishTime();
				timeSlotEnd	= list.get(i).getStartTime();
			}
			double startTime = Math.max(timeSlotStart, EST);
			//condition2：slot is large enough to support this task
			if(timeSlotEnd - startTime >= task.getTaskSize() / vm.getSpeed())	
				return startTime;
		}
		return -1;
	}
	
	private VM getEarliestVM(){
		VM ealiestVM = null;
		double earliestTime = Double.MAX_VALUE;
		for(VM vm : solution.keySet()){
			double startTime = solution.getVMLeaseStartTime(vm);
			if(startTime < earliestTime){
				earliestTime = startTime;
				ealiestVM = vm;
			}
		}
		return ealiestVM;
	}
	private VM getLatestVM(){
		VM latestVM = null;
		double latestTime = 0;
		for(VM vm : solution.keySet()){
			double finishTime = solution.getVMLeaseEndTime(vm);
			if(finishTime > latestTime){
				latestTime = finishTime;
				latestVM = vm;
			}
		}
		return latestVM;
	}
	
	
	//PSO算法
	private class PSOPCP{
		private static final double W = 0.5f, C1 = 2.0f, C2 = 2.0f; 	//parameters for PSO are from the paper
		private int popSize = 100;
		private int iteNum = 100;
		private int MaxEvalNum = 500, evalNum = 0;
		private int dimension;	//number of tasks
		private int range;
		private int MNPT;
		
		List<Task> PCP;
		private Solution lastS;
		HashMap<Task, Allocation> lastSrevMapping;
		private int taskNum;
		private int existingVmNum;
		private int newVmNum;
		private VM[] vmPool;
//		private LinkedList<VM> updatedVm = new LinkedList<VM>();
		
		private int NO_OF_ITE = 50;
		private int NO_OF_EPSILON_ITE = (int)(NO_OF_ITE*0.7);
		private double epsilonDeadline;

		private Random rnd = new Random();

		public PSOPCP(List<Task> PCP, Solution lastS, VM[] vmPool) {
			this.PCP = PCP;
			this.lastS = lastS;
			this.lastSrevMapping = lastS.getRevMapping();
			this.taskNum = PCP.size();
			this.existingVmNum = lastS.size();
			this.range = vmPool.length;
			this.MNPT = this.range/VM.TYPE_NO;
			this.vmPool = vmPool;
		}
		
		private void initalize() {
//			if(num == 1) {
//				this.popSize = 100; //taskNum * 10; //50;
//				this.iteNum = 100; //taskNum * 10; //50;
//			}
//			else {
//				this.popSize = 50; //taskNum * 10; //50;
//				this.iteNum = 100; //taskNum * 10; //50;
//			}
//			if(taskNum < 5) {
				this.popSize = 150; //taskNum * 10; //50;
				this.iteNum = 5; //taskNum * 10; //50;
//			}
//			else {
//				this.popSize = taskNum * 10; //50;
//				this.iteNum = taskNum * 10; //50;
//
//			}
				

			this.dimension = this.taskNum;
//			this.newVmNum = taskNum * VM.TYPE_NO;
			
//			this.NO_OF_ITE = this.iteNum;
//			this.NO_OF_EPSILON_ITE = (int)(NO_OF_ITE*0.7);
		}
		
		public Solution run() {

			this.initalize();
			
			double xMin = 0,  xMax = range - 1;	//boundary
			double vMax = xMax;					//maximum velocity
			double[] globalBestPos = new double[dimension];	//global Best Position
			Solution globalBestSol = null;
			Particle[] particles;
			
			{
//				Benchmarks bench = new Benchmarks(wf);
//				double maxMakespan = bench.getCheapSchedule().calcMakespan();//used to calculate epsilonDeadline
				
				particles = new Particle[this.popSize];
				//计算特定解的个数并生成特定解
				int particularSolNum = this.existingVmNum ;
				for (int i = 0; i < this.popSize; i++){		//initialize particles 
					if(i < this.existingVmNum) { //生成特定解2
						for(Map.Entry<VM, LinkedList<Allocation>> entry : this.lastS.entrySet()) {
							particles[i] = new Particle(2, entry.getKey().getId(), vMax);
							i++;
				        }
						i = this.existingVmNum-1;
					}
					else if(i == particularSolNum) { //生成特定解1
						particles[i] = new Particle(1, -1, vMax);
					}
					else
						particles[i] = new Particle(vMax, xMin, xMax);
					particles[i].generateSolution();
					
					if (globalBestSol == null || particles[i].sol.isBetterThan(globalBestSol, wf.getDeadline())) {
						for (int j = 0; j < dimension; j++)
							globalBestPos[j] = particles[i].position[j];
						globalBestSol= particles[i].sol;	// 这里不需要clone，因为particle的sol每次迭代时都会重新new的
					}
				}
	//			System.out.println("the best initial solution:"+globalBestSol.calcCost()+";\t"+globalBestSol.calcMakespan());
				
//				for (int iteIndex = 0; iteIndex < this.iteNum; iteIndex++) {
				while(this.evalNum < this.MaxEvalNum) {
//					if(maxMakespan<wf.getDeadline() || iteIndex >= NO_OF_EPSILON_ITE)
//						epsilonDeadline = wf.getDeadline();
//					else
//						epsilonDeadline = wf.getDeadline() +
//							(maxMakespan-wf.getDeadline())* Math.pow((1-(double)iteIndex/NO_OF_EPSILON_ITE), 4);
					
//					while(globalBestSol.calcMakespan() > wf.getDeadline()) {
//						iteIndex = 0;
//						break;
//					}
	//				W = (double) (1.0 - iteIndex * 0.6 / 499);	//惯性递减，比w = 1效果要好一些。
					for (int i = 0; i < this.popSize; i++) {
						for (int j = 0; j < this.dimension; j++) {
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
	//						System.out.printf("Iteration index：%3d\t%5.2f\t%5.2f\n",iteIndex,
	//								globalBestSol.calcCost(),	globalBestSol.calcMakespan());
						}
						this.evalNum++;
						if(this.evalNum >= this.MaxEvalNum)
							break;
					}
	//				System.out.printf("Iteration index：%3d\t%5.2f\t%5.2f\n",iteIndex,
	//						globalBestSol.calcCost(),	globalBestSol.calcMakespan());
				}
//				System.out.println("Globle best is :" + globalBestSol.calcCost()+";\t"+globalBestSol.calcMakespan());
			}
			//清理updateVM中没有用到的VM
//			if(xiufu) {
//				Iterator<VM> it = updatedVM.iterator();
//				while(it.hasNext()){
//					VM vm = it.next();
//					if(!globalBestSol.keySet().contains(vm))
//				        it.remove();
//				}
//			}
//			System.out.println(updatedVM.size());
			
			//设置PCP中任务的状态和属性
			for(int i = 0; i < PCP.size(); i++) {
				int j = 0;
				Task task = PCP.get(i);				
				HashMap<Task, Allocation> revMapping = globalBestSol.getRevMapping();
				Allocation alloc = revMapping.get(task);
				task.setAssigned(true);		// set all tasks of P as assigned
				task.setAST(alloc.getStartTime());
				task.setAFT(alloc.getFinishTime());
			}
			
			//globalBestSol中可能有新增加虚拟机，故VM的id重新从0开始编号
//			int id = 0;
//			for(VM vm : globalBestSol.keySet()){
//				vm.setId(id);
//				id++;
//			}
			
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
			

			/**
			 * initialize a particle + 特定的初始解
			 * 
			 * @param particularSolTypeId 生成的特定解的类型ID，1 特定解1； 2 特定解2
			 * @param vmId map到的VM ID
			 */
			public Particle(int particularSolTypeId, int vmId, double vMax){
				
				if(particularSolTypeId == 1) { //特定解1：把PCP上的任务放到各自性价比最高的机器上
					for (int i = 0; i < dimension; i++) {
						/*获得task对应的性价比最高的Vm类型, 并确定该类型在vmPool中的index, 并随机选择某个index*/
						//获得task对应的性价比最高的Vm类型
						Task t = PCP.get(i);
						int bestVmType = mostCostEffectiveVmType.get(t).intValue();
						//确定该类型在vmPool中的index
						List<Integer> bestVmIndexs = new ArrayList<Integer>();
						for(int k = 0; k < vmPool.length; k++) { 
							if(vmPool[k].getType() == bestVmType)
								bestVmIndexs.add(new Integer(k));
						}
						//随机选择某个index
						int random = (int)(bestVmIndexs.size() * rnd.nextDouble());
						this.position[i] = bestVmIndexs.get(random).intValue(); 
					}
				}
				else if(particularSolTypeId == 2) { //特定解2：把PCP上的任务放到一个Vm(existing VM + new每一种类型)上
					for (int i = 0; i < dimension; i++){
						this.position[i] = vmId; 
						this.speed[i] = vMax * rnd.nextDouble() - vMax/2;			
						this.bestPos[i] = this.position[i];	
					}
				}
				else
					System.out.println("不存在该类型的特殊解！！！");
			}
			
			public void generateSolution() {		//generate solution from position
				this.sol = new Solution(0);
				
				//VMPool中vm的id和index相等！！！
				VM vm = vmPool[0]; //task分配的VM

				/*初始化当前Particle task和vm的map关系,以及与PCP一起的任务*/		
				HashMap<Task, VM> particleTasksMap = new HashMap<Task, VM>();
				HashMap<Integer, LinkedList<Task>> particleVmsMap = new HashMap<Integer, LinkedList<Task>>();
				//与PCP分配在一起的任务（包含PCP本身）,用于判断升级时是否new新的机器。与PCP在一起的任务升级后的VM都是VMPool中的new VM
				Set<Task> withPCPTasks = new HashSet<Task>(); 
				//获得lastS task和vm的map关系
				for(VM v : lastS.keySet()){
					LinkedList<Task> tasks = new LinkedList<Task>();
					LinkedList<Allocation> allocList = lastS.get(v);
					for(Allocation alloc : allocList){
						Task task = alloc.getTask();
						tasks.add(task);
						particleTasksMap.put(task, v);
					}
					particleVmsMap.put(new Integer(v.getId()), tasks);
				}
				//获得PCP task和vm的map关系
				int pcpTaskIndex = 0;
				for(Task t : PCP) {
					int vmIndex = (int)(Math.floor(position[pcpTaskIndex])); //向下取整
					vm = vmPool[vmIndex];
					pcpTaskIndex++;
					
					particleTasksMap.put(t, vm);
					Integer vmInt = new Integer(vm.getId());
					if(particleVmsMap.containsKey(vmInt)) {
						particleVmsMap.get(vmInt).add(t);
					}
					else {
						LinkedList<Task> tastList = new LinkedList<Task>();
						tastList.add(t);
						particleVmsMap.put(vmInt, tastList);
					}	
					
					//添加与PCP一起的任务
					for(Task t1 : particleVmsMap.get(vmInt))
						withPCPTasks.add(t1);
				}
				
				//初始化已经使用的VM
				Set<Integer> usedVMId = new HashSet<Integer>();
				for(Integer v : particleVmsMap.keySet())
					usedVMId.add(v);
				

				for(int  i = 1; i < wf.size()-1; i++){
					Task task = wf.get(i);		// tasks in wf is a topological sort
					
					int vmId = -1;
					int updatedVmId = -1;
					if(particleTasksMap.containsKey(task)) {
						vm = particleTasksMap.get(task);
						vmId = vm.getId();
					}
					else
						continue;
			
					double startTime = calcESTforListPSO(task, vm);
					sol.addTaskToVM(vm, task, startTime, true);
					
					int vmType = vm.getType();
					LinkedList<Allocation> allocList = sol.get(vm); //当前vm上的task
					int flag = 0 ;
					if(xiufu) {
					while(sol.getRevMapping().get(task).getFinishTime() > task.getLFT() + Evaluate.E && vmType < VM.FASTEST){
						vmType = vmType + 1;
						
						if(allocList != null) {
							for(Allocation alloc : allocList){
								double newFinishTime = alloc.getTask().getTaskSize() / VM.SPEEDS[vmType] + alloc.getStartTime();
								alloc.setFinishTime(newFinishTime);
							}
						}
						else{
							System.out.println("修复存在问题");
							throw new RuntimeException();
						}
						flag++;
					}}
					if(flag > 0) { //说明vm升级了
						VM updatedVm = vm; //升级后的VM
						//vm升级，原先占用的VMIndex将会释放，释放原先的index
						if(usedVMId.contains(new Integer(vmId)))
							usedVMId.remove(new Integer(vmId));
						//确定升级后VM在VMPool中的位置index
						int flg = 0;
						int startIndex = vmType * MNPT;
						for(int j = 0; j < MNPT; j++) {
							updatedVmId = startIndex + j;
							if(!usedVMId.contains(new Integer(updatedVmId)))
								break;
							else
								flg++;
						}
						updatedVm = vmPool[updatedVmId];
						if(flg == MNPT){ //vmType的虚拟机已经全部被使用，此时只更改当前vm的类型即可
							vm.setType(vmType);
							updatedVm = vm;
							updatedVmId = updatedVm.getId();
						}
						
						//更新particleTasksMap和particleVmsMap、usedVMId
						LinkedList<Task> tastList = particleVmsMap.get(new Integer(vmId));
						for(Task t : tastList) 
							particleTasksMap.put(t, updatedVm);
						particleVmsMap.remove(new Integer(vmId));
						particleVmsMap.put(new Integer(updatedVmId), tastList);
						usedVMId.add(new Integer(updatedVmId));
						
						//更新sol
						sol.remove(vm); //移除升级前的映射, list还在
						for(Allocation alloc : allocList){
							double newFinishTime = alloc.getTask().getTaskSize() / updatedVm.getSpeed() + alloc.getStartTime();
							alloc.setFinishTime(newFinishTime);
							alloc.setVM(updatedVm);
						}
						sol.put(updatedVm, allocList);
					}	
				}
				//PCP中的position更新为最终sol的(升级后的)
				pcpTaskIndex = 0;
				for(Task t : PCP) {
					position[pcpTaskIndex] = particleTasksMap.get(t).getId() + 0.5;
					if(position[pcpTaskIndex] > (vmPool.length-1+0.5)){
						System.out.println("升级存在问题！！！2");
						throw new RuntimeException();
					}
					pcpTaskIndex++;
				}
				
				
//				System.out.println("跳出for循环");
				//record the best solution this particle has found
				if (bestSol==null || this.sol.isBetterThan(bestSol, wf.getDeadline())){
					for (int j = 0; j < dimension; j++)
						this.bestPos[j] = this.position[j];	
					this.bestSol = this.sol;	
				}
			}
			
			//没有升级
			public void generateSolution1() {		//generate solution from position
				this.sol = new Solution(0);
				
				int pcpTaskIndex = 0;
				for(int  i = 1; i < wf.size()-1; i++){
//					System.out.println("第i个任务"+ i);
					Task task = wf.get(i);		// tasks in wf is a topological sort
					int vmIndex = -1; //task分配的VM序号
					VM vm = vmPool[0]; //task分配的VM
					
					if(PCP.contains(task)) { //assignning task
						vmIndex = (int)(Math.floor(position[pcpTaskIndex])); //向下取整
						vm = vmPool[vmIndex];
						pcpTaskIndex++;
					}
					else if(lastSrevMapping.containsKey(task)) { //assigned task
						vm = lastSrevMapping.get(task).getVM();
					}
					else
						continue;
					
					double startTime = -1;
					startTime = calcESTforListPSO(task, vm);
					
					sol.addTaskToVM(vm, task, startTime, true);
					
				}

				if (bestSol==null || this.sol.isBetterThan(bestSol, wf.getDeadline())){
					for (int j = 0; j < dimension; j++)
						this.bestPos[j] = this.position[j];	
					this.bestSol = this.sol;	
				}
			}
			
			public double calcESTforListPSO(Task task, VM vm){
				HashMap<Task, Allocation> revMapping1 = sol.getRevMapping();
				double EST = 0; 			
				for(Edge inEdge : task.getInEdges()){
					Task parent = inEdge.getSource();
					if(revMapping1.containsKey(parent)) {
						Allocation alloc = revMapping1.get(parent);
						VM parentVM = alloc.getVM();
						double arrivalTime = alloc.getFinishTime();
						//最晚父任务的传输时间
						if( parentVM != vm )
							arrivalTime += inEdge.getDataSize() / VM.NETWORK_SPEED;
						EST = Math.max(EST, arrivalTime);
					}
					else if(parent.getName() == "entry")
						EST =  Math.max(EST, 0);
					else {
						EST = Math.max(EST, parent.getEFT() + inEdge.getDataSize() / VM.NETWORK_SPEED);
					}
				}
				if(!sol.containsKey(vm) || sol.get(vm) == null)
					EST = Math.max(EST, 0);
				else {
					LinkedList<Allocation> allocations = sol.get(vm);
					double vmReadyTime = allocations.get(allocations.size()-1).getFinishTime();
					EST = Math.max(EST, vmReadyTime);
				}	
				return EST;
			}

			public String toString() {
				if(sol != null)
					return "Particle [" + sol.calcCost()+ ", " + sol.calcMakespan()+ "]";
				return "";
			}
		}
	}
	
	public static class VMTaskComparator implements Comparator<Allocation>{
		public int compare(Allocation o1, Allocation o2) {
			if(o1.getStartTime() > o2.getStartTime())
				return 1;
			else if(o1.getStartTime() < o2.getStartTime())
				return -1;
			return 0;
		}
	}
}
