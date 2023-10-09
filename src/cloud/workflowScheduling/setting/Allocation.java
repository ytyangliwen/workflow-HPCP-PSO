package cloud.workflowScheduling.setting;

import java.io.Serializable;

public class Allocation implements Serializable{

	private Task task;
	private VM vm;
	private double startTime; //ylw190426 任务的开始时间和结束时间
	private double finishTime;
	
	public Allocation() {}
	public Allocation(VM vm, Task task, double startTime, double finishTime) { //用于深复制
		this.vm = vm;
		this.task = task;
		this.startTime = startTime;
		this.finishTime = finishTime;
	}
	public Allocation(VM vm, Task task, double startTime) {
		this.vm = vm;
		this.task = task;
		this.startTime = startTime;
		if(vm != null && task !=null)
			this.finishTime = startTime + task.getTaskSize() / vm.getSpeed();
	}

	//-------------------------------------getters & setters--------------------------------
	public VM getVM(){
		return vm;
	}
	public Task getTask() {
		return task;
	}
	public double getStartTime() {
		return startTime;
	}
	public double getFinishTime() {
		return finishTime;
	}
	public void setVM(VM vm) {
		this.vm = vm;
	}
	public void setTask(Task task) {
		this.task = task;
	}
	public void setStartTime(double startTime) {
		this.startTime = startTime;
	}
	public void setFinishTime(double finishTime) {
		this.finishTime = finishTime;
	}
	
	//-------------------------------------overrides--------------------------------
	public String toString() {
		return "Allocation [task=" + task + ", startTime="
				+ startTime + ", finishTime=" + finishTime + "]";
	}
	
	//-------------------------------------only for ICPCP---------------------------
	public Allocation(int vmId, Task task, double startTime) {
		this.vm = null;
		this.task = task;
		this.startTime = startTime;
		this.finishTime = startTime + task.getTaskSize() / VM.SPEEDS[vmId];
	}
}