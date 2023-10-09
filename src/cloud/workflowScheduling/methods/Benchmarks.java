package cloud.workflowScheduling.methods;

import java.util.*;
import cloud.workflowScheduling.setting.*;


public class Benchmarks { //两种用的所谓HEFT，都没有插空
	private Solution cheapSchedule, fastSchedule;
	private Solution minCostSchedule; //文献8的公式(5)(6)的调度结果

	public Benchmarks(Workflow wf){
		fastSchedule =  bLevelEST(wf);		//VM固定为fastest且可以任意多个，求取近似的最快调度方案
		cheapSchedule = slowestVMEST(wf);	//uses one slowest VM，求最慢的调度方案	
		
		minCostSchedule = minCostOfSingleTask(wf);
	}
	
	// in one slowest VM, use EST to allocate tasks
	private Solution slowestVMEST(Workflow wf){ //HEFT算法，用的排序是L-ACO文中的blevel
		Solution solution = new Solution();
		
		//原先的
		VM vm = new VM(VM.SLOWEST);
		for(Task task : wf){
			double EST = solution.calcEST(task, vm);
			solution.addTaskToVM(vm, task, EST, true);
		}
		
//		//修改的
//		for(Task task : wf){				//select VM based on EST
//			double minEST = Double.MAX_VALUE;
//			VM selectedVM = null;
//			for(VM vm : solution.keySet()){				// calculate EST of task on all the used VMs
//				double EST = solution.calcEST(task, vm);
//				if(EST<minEST){
//					minEST = EST;
//					selectedVM = vm;
//				}
//			}
//			//在云中使用需要扩展的点：何时加入新VM
//			double EST = solution.calcEST(task, null);	//whether minEST can be shorten if a new vm is added
//			if(EST < minEST){
//				minEST = EST;
//				selectedVM = new VM(VM.SLOWEST);
//			}
//			solution.addTaskToVM(selectedVM, task, minEST, true);	//allocation
//		}
		
		return solution;
	}
	
	//list scheduling based on bLevel and EST; a kind of HEFT 
	//动态增加虚拟机的个数，开始没有确定VM的个数。如果增加VM，EST变小，则增加新的VM
	private Solution bLevelEST(Workflow wf) {//HEFT算法，用的排序是L-ACO文中的blevel
		Solution solution = new Solution();
		
		//排序可以不要，workflow中排序了
		List<Task> tasks = new ArrayList<Task>(wf);
		Collections.sort(tasks, new Task.BLevelComparator()); 	//sort based on bLevel
		Collections.reverse(tasks); 	// larger first	
		
		//原先的
//		for(Task task : tasks){				//select VM based on EST
//			double minEST = Double.MAX_VALUE;
//			VM selectedVM = null;
//			for(VM vm : solution.keySet()){				// calculate EST of task on all the used VMs
//				double EST = solution.calcEST(task, vm);
//				if(EST<minEST){
//					minEST = EST;
//					selectedVM = vm;
//				}
//			}
//			//在云中使用需要扩展的点：何时加入新VM
//			double EST = solution.calcEST(task, null);	//whether minEST can be shorten if a new vm is added
//			if(EST < minEST){
//				minEST = EST;
//				selectedVM = new VM(VM.FASTEST);
//			}
//			solution.addTaskToVM(selectedVM, task, minEST, true);	//allocation
//		}
		
		//新的
		for(Task task : wf){
			VM vm = new VM(VM.FASTEST);
			double EST = solution.calcEST(task, vm);
			solution.addTaskToVM(vm, task, EST, true);
		}
		
		return solution;
	}
	
	/**
	 * 文献8中的公式(5)(6), 每个任务分配到cost最小的机器上，没有考虑传输时间
	 * @param wf
	 * @return
	 */
	private Solution minCostOfSingleTask(Workflow wf) {
		Solution solution = new Solution();
		
		List<Task> tasks = new ArrayList<Task>(wf);
		Collections.sort(tasks, new Task.BLevelComparator()); 	//sort based on bLevel
		Collections.reverse(tasks); 	// larger first
		
		for(Task task : tasks){				//select VM based on EST
			double minCost = Double.MAX_VALUE;
			int selectedVMType = -1;
			for(int k = 0 ; k < VM.TYPE_NO; k++){				// calculate EST of task on all the used VMs
				VM tempVM = new VM(k);
				double cost = Math.ceil(task.getTaskSize()/VM.SPEEDS[k]/VM.INTERVAL)*VM.UNIT_COSTS[k];
				if(cost < minCost){
					minCost = cost;
					selectedVMType = k;
				}
			}
			double EST = solution.calcEST(task, null);
			solution.addTaskToVM(new VM(selectedVMType), task, EST, true);	//allocation
		}
		return solution;
	}

	//----------------------------getters-------------------------------------
	public Solution getCheapSchedule() {
		return cheapSchedule;
	}
	public Solution getFastSchedule() {
		return fastSchedule;
	}
	public Solution getMinCost8Schedule() {
		return minCostSchedule;
	}
}