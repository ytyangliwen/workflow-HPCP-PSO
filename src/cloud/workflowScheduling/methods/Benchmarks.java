package cloud.workflowScheduling.methods;

import java.util.*;
import cloud.workflowScheduling.setting.*;


public class Benchmarks { //�����õ���νHEFT����û�в��
	private Solution cheapSchedule, fastSchedule;
	private Solution minCostSchedule; //����8�Ĺ�ʽ(5)(6)�ĵ��Ƚ��

	public Benchmarks(Workflow wf){
		fastSchedule =  bLevelEST(wf);		//VM�̶�Ϊfastest�ҿ�������������ȡ���Ƶ������ȷ���
		cheapSchedule = slowestVMEST(wf);	//uses one slowest VM���������ĵ��ȷ���	
		
		minCostSchedule = minCostOfSingleTask(wf);
	}
	
	// in one slowest VM, use EST to allocate tasks
	private Solution slowestVMEST(Workflow wf){ //HEFT�㷨���õ�������L-ACO���е�blevel
		Solution solution = new Solution();
		
		//ԭ�ȵ�
		VM vm = new VM(VM.SLOWEST);
		for(Task task : wf){
			double EST = solution.calcEST(task, vm);
			solution.addTaskToVM(vm, task, EST, true);
		}
		
//		//�޸ĵ�
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
//			//������ʹ����Ҫ��չ�ĵ㣺��ʱ������VM
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
	//��̬����������ĸ�������ʼû��ȷ��VM�ĸ������������VM��EST��С���������µ�VM
	private Solution bLevelEST(Workflow wf) {//HEFT�㷨���õ�������L-ACO���е�blevel
		Solution solution = new Solution();
		
		//������Բ�Ҫ��workflow��������
		List<Task> tasks = new ArrayList<Task>(wf);
		Collections.sort(tasks, new Task.BLevelComparator()); 	//sort based on bLevel
		Collections.reverse(tasks); 	// larger first	
		
		//ԭ�ȵ�
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
//			//������ʹ����Ҫ��չ�ĵ㣺��ʱ������VM
//			double EST = solution.calcEST(task, null);	//whether minEST can be shorten if a new vm is added
//			if(EST < minEST){
//				minEST = EST;
//				selectedVM = new VM(VM.FASTEST);
//			}
//			solution.addTaskToVM(selectedVM, task, minEST, true);	//allocation
//		}
		
		//�µ�
		for(Task task : wf){
			VM vm = new VM(VM.FASTEST);
			double EST = solution.calcEST(task, vm);
			solution.addTaskToVM(vm, task, EST, true);
		}
		
		return solution;
	}
	
	/**
	 * ����8�еĹ�ʽ(5)(6), ÿ��������䵽cost��С�Ļ����ϣ�û�п��Ǵ���ʱ��
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