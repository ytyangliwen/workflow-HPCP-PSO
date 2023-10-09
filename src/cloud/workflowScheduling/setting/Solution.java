package cloud.workflowScheduling.setting;

import java.io.IOException;
import java.util.*;

import cloud.workflowScheduling.*;


//Allocation List is sorted based on startTime
public class Solution extends LinkedHashMap<VM, LinkedList<Allocation>>{

	private static final long serialVersionUID = 1L;
	
	//the content in revMapping is the same as that in HashMap<VM, LinkedList<Allocation>>
	//used to make get_Allocation_by_Task easy 
	private HashMap<Task, Allocation> revMapping = new HashMap<Task, Allocation>();	//reverseMapping
	
	public Solution() {
		super();
		VM.resetInternalId();
	}
	public Solution(int i) { //该构造函数只为了不初始化VM的id为0
		super();
	}
	
	//----------------------------------------add a task-------------------------------------------
	//isEnd denotes whether the task is placed at the end, or the beginning
	public void addTaskToVM(VM vm, Task task, double startTime, boolean isEnd){
		if(this.containsKey(vm) == false)
			this.put(vm, new LinkedList<Allocation>());
		
		Allocation alloc = new Allocation(vm, task, startTime);
		
		boolean conflict = false;				//check whether there is time conflict
		double startTime1 = alloc.getStartTime();
		double finishTime1 = alloc.getFinishTime();
		for(Allocation prevAlloc : this.get(vm)){
			double startTime2 = prevAlloc.getStartTime();
			double finishTime2 = prevAlloc.getFinishTime();
			if((startTime1>startTime2 && startTime2>finishTime1)	  //startTime2 is between startTime1 and finishTime1
				|| (startTime2>startTime1 && startTime1>finishTime2)) //startTime1 is between startTime2 and finishTime2
				conflict = true;
		}
		if(conflict)
			throw new RuntimeException("Critical Error: Allocation conflicts");

		if(isEnd)
			this.get(vm).add(alloc);
		else
			this.get(vm).add(0, alloc);
		revMapping.put(alloc.getTask(), alloc);
	}
	

	
	public void updateVM(VM vm){		//这里仅进行该VM上的更新，其他的vm的就不再涉及了
		vm.setType(vm.getType()+1);
		
		LinkedList<Allocation> list = this.get(vm);
		if(list == null)
			return;
		for(Allocation alloc : list){
			double newFinishTime = alloc.getTask().getTaskSize() / vm.getSpeed() + alloc.getStartTime();
			alloc.setFinishTime(newFinishTime);
		}
	}
	
	public VM updateVMForPCPandPSO2_1(VM vm){		//这里仅进行该VM上的更新，其他的vm的就不再涉及了
		LinkedList<Allocation> list = this.get(vm);
		this.remove(vm); //移除映射, list还在
		
		int type = vm.getType() + 1;
		VM newVm = new VM(type);
		
		if(list == null)
			return null;
		for(Allocation alloc : list){
			double newFinishTime = alloc.getTask().getTaskSize() / newVm.getSpeed() + alloc.getStartTime();
			alloc.setFinishTime(newFinishTime);
			alloc.setVM(newVm);
		}
		this.put(newVm, list);
		return newVm;
	}

	//----------------------------------------calculations-------------------------------------------
	//calculate Earliest Starting Time of task on vm	
	public double calcEST(Task task, VM vm){
		double EST = 0; 			
		for(Edge inEdge : task.getInEdges()){
			Task parent = inEdge.getSource();
			Allocation alloc = revMapping.get(parent);
			VM parentVM = alloc.getVM();
			double arrivalTime = alloc.getFinishTime();
			//最晚父任务的传输时间
			if( parentVM != vm )
				arrivalTime += inEdge.getDataSize() / VM.NETWORK_SPEED;
			EST = Math.max(EST, arrivalTime);
		}
		if(vm == null)
			EST = Math.max(EST, VM.LAUNCH_TIME);
		else
			EST = Math.max(EST, this.getVMReadyTime(vm));
		return EST;
	}
	
//	public double testcalcEST(Task task, VM vm, Workflow wf){
//		double EST = 0; 			
//		for(Edge inEdge : task.getInEdges()){
//			int taskid=wf.indexOf(task);
//			Task parent = inEdge.getSource();
//			Allocation alloc = revMapping.get(parent);
//			int ptaskid=wf.indexOf(parent);
//			if(alloc == null)
//				System.out.println("null");
//			VM parentVM = alloc.getVM();
//			double arrivalTime = alloc.getFinishTime();
//			//计算的是不在同一资源上的所有父任务的传输时间，不是最晚父任务的传输时间
//			if( parentVM != vm )
//				arrivalTime += inEdge.getDataSize() / VM.NETWORK_SPEED;
//			EST = Math.max(EST, arrivalTime);
//		}
//		if(vm == null)
//			EST = Math.max(EST, VM.LAUNCH_TIME);
//		else
//			EST = Math.max(EST, this.getVMReadyTime(vm));
//		return EST;
//	}
	
	public double calcCost(){
		double totalCost = 0;
		for(VM vm : this.keySet()){
			double vmCost = calcVMCost(vm); 
			totalCost += vmCost;
		}
		return totalCost;
	}
	public double calcVMCost(VM vm){
		return vm.getUnitCost() * Math.ceil((this.getVMLeaseEndTime(vm) - this.getVMLeaseStartTime(vm))/VM.INTERVAL);
		
//		//容器计价模型
//		double leaseTime = this.getVMLeaseEndTime(vm) - this.getVMLeaseStartTime(vm);
//		long chargingTime = (long) leaseTime;
//		if((leaseTime- chargingTime) > 0.5)
//			chargingTime++;
//		return vm.getUnitCost() * chargingTime;
	}
	
	
	public double calcMakespan(){
		double makespan = -1;
		for(VM vm : this.keySet()){
			double finishTime = this.getVMReadyTime(vm);	//finish time of the last task
			if(finishTime > makespan)
				makespan = finishTime;
		}
		return makespan;
	}

	// compare this solution to Solution s; if ==, returns false; used by ACO, PSO
	public boolean isBetterThan(Solution s, double epsilonDeadline){
		double makespan1 = this.calcMakespan();
		double makespan2 = s.calcMakespan();
		double cost1 = this.calcCost();
		double cost2 = s.calcCost();
		
//		//ACS_8约束的处理
//		if(makespan1>epsilonDeadline)
//			cost1 = 1000000;
//		if(makespan2>epsilonDeadline)
//			cost2 = 1000000;
		
		if(makespan1 <= epsilonDeadline && makespan2<= epsilonDeadline ){	//both satisfy deadline
			return cost1<cost2;
		}else if(makespan1 > epsilonDeadline && makespan2 > epsilonDeadline ){//both does not satisfy
			return makespan1<makespan2;
		}else if(makespan1 <= epsilonDeadline && makespan2 > epsilonDeadline){ //this satisfy，s doesn't
			return true;
		}else if(makespan1 > epsilonDeadline && makespan2 <= epsilonDeadline) //this don't，s satisfies
			return false;
		
		return true;
	}
	//check whether there is time conflict in this schedule solution
	public boolean validate(Workflow wf){
		List<Allocation> list = new ArrayList<Allocation>(revMapping.values());
		
		Set<Task> set = new HashSet<Task>();
		for(Allocation alloc : list)
			set.add(alloc.getTask());
		if(set.size() != wf.size())	{	//check # of tasks
			return false;
		}
		
//		Collections.sort(list);			//把该solution当中的task以时间顺序排列，并检测是否是拓扑排序
		for(Allocation alloc : list){
			Task task = alloc.getTask();	// check each task and its children
			for(Edge e : task.getOutEdges()){
				Task child = e.getDestination();
				
				Allocation childAlloc = this.revMapping.get(child);
				boolean isValid = false;
				if(alloc.getVM() != childAlloc.getVM() 				
						&& alloc.getFinishTime() +e.getDataSize()/VM.NETWORK_SPEED <= childAlloc.getStartTime()+ Evaluate.E)
					isValid = true;
				else if(alloc.getVM() == childAlloc.getVM() 				
						&& alloc.getFinishTime() <= childAlloc.getStartTime() + Evaluate.E)
					isValid = true;
				if(isValid == false)
					return false;
			}
		}
		return true;
	}
	
	//check whether there is time conflict in this schedule solution
	//+返回违反约束的父子任务Id
	public List<Integer> validateId(Workflow wf){
		List<Allocation> list = new ArrayList<Allocation>(revMapping.values());
		
		List<Integer> result = new ArrayList<Integer>();
		Set<Task> set = new HashSet<Task>();
		for(Allocation alloc : list)
			set.add(alloc.getTask());
		
//		List<Integer> list1 = new LinkedList<Integer>();
//		for(int i =0;i<wf.size();i++) {
//			list1.add(new Integer(i));
//		}
//		for(Task t : set) {
//			if(wf.contains(t))
//				list1.remove(new Integer(t.getId()));
//		}
		if(set.size() != wf.size())	{	//check # of tasks
			result.add(new Integer(0));
//			System.out.println("!!!任务个数不对");
			return result;
		}
		
//		Collections.sort(list);			//把该solution当中的task以时间顺序排列，并检测是否是拓扑排序
		for(Allocation alloc : list){
			Task task = alloc.getTask();	// check each task and its children
			for(Edge e : task.getOutEdges()){
				Task child = e.getDestination();
				
				Allocation childAlloc = this.revMapping.get(child);
				boolean isValid = false;
				if(alloc.getVM() != childAlloc.getVM() 				
						&& alloc.getFinishTime() +e.getDataSize()/VM.NETWORK_SPEED <= childAlloc.getStartTime()+ Evaluate.E)
					isValid = true;
				else if(alloc.getVM() == childAlloc.getVM() 				
						&& alloc.getFinishTime() <= childAlloc.getStartTime() + Evaluate.E)
					isValid = true;
				if(isValid == false) {
					result.add(new Integer(0)); 
					result.add(new Integer(task.getId()));
					result.add(new Integer(child.getId()));
					return result;
				}
			}
		}
		result.add(new Integer(1));
		return result;
	}
		
	//----------------------------------------getters-------------------------------------------
	//VM's lease start time and finish time are calculated based on allocations
	public double getVMLeaseStartTime(VM vm){	
		if(this.get(vm).size() == 0)
			return VM.LAUNCH_TIME;
		else{
			Task firstTask = this.get(vm).get(0).getTask();
			double ftStartTime = this.get(vm).get(0).getStartTime(); // startTime of first task
			
			double maxTransferTime = 0;
			for(Edge e : firstTask.getInEdges()){
				Allocation alloc = revMapping.get(e.getSource());
				if(alloc == null || alloc.getVM() != vm)		// parentTask's VM != vm
					maxTransferTime = Math.max(maxTransferTime, e.getDataSize() / VM.NETWORK_SPEED);
			}
			return ftStartTime - maxTransferTime;
		}
	}
	public double getVMLeaseEndTime(VM vm){
		if(this.get(vm)== null || this.get(vm).size() == 0)
			return VM.LAUNCH_TIME;
		else{
			LinkedList<Allocation> allocations = this.get(vm);
			
			Task lastTask = allocations.get(allocations.size()-1).getTask();
			double ltFinishTime = allocations.get(allocations.size()-1).getFinishTime(); // finishTime of last task
			
			double maxTransferTime = 0;
			for(Edge e : lastTask.getOutEdges()){
				Allocation alloc = revMapping.get(e.getDestination());
				if(alloc == null || alloc.getVM() != vm)		// childTask's VM != vm
					maxTransferTime = Math.max(maxTransferTime, e.getDataSize() / VM.NETWORK_SPEED);
			}
			return ltFinishTime + maxTransferTime;
		}
	}
	//note the difference between VMReadyTime and VMLeaseEndTime.
	public double getVMReadyTime(VM vm){		//finish time of the last task
		if(this.get(vm)== null || this.get(vm).size() == 0)
			return VM.LAUNCH_TIME;
		else{
			LinkedList<Allocation> allocations = this.get(vm);
			return allocations.get(allocations.size()-1).getFinishTime(); 
		}
	}
	public HashMap<Task, Allocation> getRevMapping() {
		return revMapping;
	}
	
	//vm task 不需要深复制
	public static Solution deepcopy(Solution s1) {
//		VM v1 =new VM(0), v2= new VM(1); Task t1 = new Task("liwen",100);
//		Allocation newAllo1 = new Allocation(v1, t1, 0, 1);
//		Allocation newAllo2 = new Allocation(v1, t1, 0, 2);
//		Allocation newAllo3 = new Allocation(v1, t1, 0, 3);
//		LinkedList<Allocation> newAlloList1 = new LinkedList<Allocation>(); newAlloList1.add(newAllo1);newAlloList1.add(newAllo2);
//		LinkedList<Allocation> newAlloList2 = new LinkedList<Allocation>(); newAlloList2.add(newAllo2);newAlloList2.add(newAllo3);
//		s1.put(v1, newAlloList1);s1.put(v2, newAlloList2);
//		s1.revMapping.put(t1, newAllo1);
//		HashMap<Task, Allocation> revMapping1 = s1.getRevMapping();

		
		Solution newS = new Solution(0);
//		HashMap<Task, Allocation> revMapping2 = newS.getRevMapping();
		for(VM vm : s1.keySet()) {
			LinkedList<Allocation> newAlloList = new LinkedList<Allocation>();
			for(Allocation allo : s1.get(vm)) {
				Allocation newAllo = new Allocation(allo.getVM(), allo.getTask(), allo.getStartTime(), allo.getFinishTime());
				newAlloList.add(newAllo);
				newS.revMapping.put(newAllo.getTask(), newAllo);
			}
			newS.put(vm, newAlloList);
			
		}
		
//		newS.get(v1).get(0).setFinishTime(55555);
//		newS.get(v1).remove(0);
//		newS.get(v2).remove(1);
//		LinkedList<Allocation> newAlloList3=s1.get(v1);
//			
//		revMapping.put(alloc.getTask(), alloc);
		return newS;
	}

	//----------------------------------------override-------------------------------------------
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("required cost：" + this.calcCost() + "\trequired time：" + this.calcMakespan()+"\r\n");
//		for(VM vm : this.keySet()){
//			sb.append(vm.toString() + this.get(vm).toString()+"\r\n");
//		}
		return sb.toString();
	}
	
	// ----------------------------------these three functions only used by ICPCP-------------------

}