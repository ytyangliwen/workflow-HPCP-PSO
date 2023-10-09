package cloud.workflowScheduling.test;

import java.util.*;

import cloud.workflowScheduling.methods.Scheduler;
import cloud.workflowScheduling.setting.*;

public class HEFT{
	private Workflow wf;
	private int range;
	private Random rnd = new Random();
	
	private int dimension;	//number of tasks
	private VM[] vmPool;
	public double[] position;

    private Map<Task, Map<VM, Double>> computationCosts;
    private Map<Task, Map<Task, Double>> transferCosts;
    private Map<Task, Double> rank;
    private Map<VM, List<Event>> schedules;
    private Map<Task, Double> earliestFinishTimes;
    private double averageBandwidth;

    private class Event {

        public double start;
        public double finish;

        public Event(double start, double finish) {
            this.start = start;
            this.finish = finish;
        }
    }

    private class TaskRank implements Comparable<TaskRank> {

        public Task task;
        public Double rank;

        public TaskRank(Task task, Double rank) {
            this.task = task;
            this.rank = rank;
        }

        @Override
        public int compareTo(TaskRank o) {
            return o.rank.compareTo(rank);
        }
    }


    public HEFT(Workflow wf, VM[] vmPool) {
    	this.wf = wf;
		this.dimension = wf.size();
		this.vmPool = vmPool;
		this.range = vmPool.length;
		
		position = new double[dimension];
		
        computationCosts = new HashMap<>();
        transferCosts = new HashMap<>();
        rank = new HashMap<>();
        earliestFinishTimes = new HashMap<>();
        schedules = new HashMap<>();
    	
    }
    /**
     * The main function
     */
    public double[] run() {

        averageBandwidth = VM.NETWORK_SPEED;

        for (VM vm : vmPool) {
            schedules.put(vm, new ArrayList<Event>());
        }

        // Prioritization phase
        calculateComputationCosts();
        calculateTransferCosts();
        calculateRanks(); //计算rank的时候没有考虑论文中提出的tie-breaking yls190312

        // Selection phase
        allocateTasks();
        
        return position;
    }


    /**
     * Populates the computationCosts field with the time in seconds to compute
     * a task in a vm.
     */
    private void calculateComputationCosts() {
        for (Task task : wf) {
            Map<VM, Double> costsVm = new HashMap<>();
            for (VM vm : vmPool) {
                   costsVm.put(vm,
                		   task.getTaskSize() / vm.getSpeed());
            }
            computationCosts.put(task, costsVm);
        }
    }

    /**
     * Populates the transferCosts map with the time in seconds to transfer all
     * files from each parent to each child
     */
    private void calculateTransferCosts() {
        // Initializing the matrix
    	for (Task task1 : wf) {
            Map<Task, Double> taskTransferCosts = new HashMap<>();
            for (Task task2 : wf) {
                taskTransferCosts.put(task2, 0.0);
            }
            transferCosts.put(task1, taskTransferCosts);
        }

        // Calculating the actual values
        for (Task parent : wf) {
            for(Edge e : parent.getOutEdges()){
            	Task child = e.getDestination();
                transferCosts.get(parent).put(child, (double) (e.getDataSize() / VM.NETWORK_SPEED));
            }
        }
    }


    /**
     * Invokes calculateRank for each task to be scheduled
     */
    private void calculateRanks() {
    	for (Task task : wf) {
            calculateRank(task);
        }
    }

    /**
     * Populates rank.get(task) with the rank of task as defined in the HEFT
     * paper.
     *
     * @param task The task have the rank calculates
     * @return The rank
     */
    private double calculateRank(Task task) {
        if (rank.containsKey(task)) {
            return rank.get(task);
        }

        double averageComputationCost = 0.0;

        for (Double cost : computationCosts.get(task).values()) {
            averageComputationCost += cost;
        }

        averageComputationCost /= computationCosts.get(task).size();

        double max = 0.0;
        for(Edge e : task.getOutEdges()){
             Task child = e.getDestination();
            double childCost = transferCosts.get(task).get(child)
                    + calculateRank(child);
            max = Math.max(max, childCost);
        }

        rank.put(task, averageComputationCost + max);

        return rank.get(task);
    }

    /**
     * Allocates all tasks to be scheduled in non-ascending order of schedule.
     */
    private void allocateTasks() {
        List<TaskRank> taskRank = new ArrayList<>();
        for (Task task : rank.keySet()) {
        	//避免虚拟入口任务排在后边
        	if(task.getId() == wf.get(0).getId())
        		taskRank.add(new TaskRank(task, rank.get(task)+1));
        	else if(task.getId() == wf.get(wf.size()-1).getId())
        		 taskRank.add(new TaskRank(task, rank.get(task)-1));
            taskRank.add(new TaskRank(task, rank.get(task)));
        }

        // Sorting in non-ascending order of rank
        Collections.sort(taskRank);
        for (TaskRank rank : taskRank) {
            allocateTask(rank.task);
        }
    }

    /**
     * Schedules the task given in one of the VMs minimizing the earliest finish
     * time
     *
     * @param task The task to be scheduled
     * @pre All parent tasks are already scheduled
     */
    private void allocateTask(Task task) {
        VM chosenVM = null;
        int chosenVmIndex = -1;
        double earliestFinishTime = Double.MAX_VALUE;
        double bestReadyTime = 0.0;
        double finishTime;

        for(int vmIndex = 0; vmIndex < vmPool.length; vmIndex++){
			VM vm = vmPool[vmIndex];
            double minReadyTime = 0.0;

            for(Edge e : task.getInEdges()){
                Task parent = e.getSource();
                double readyTime = earliestFinishTimes.get(parent);
                if (((int)position[parent.getId()]) != vmIndex) {
                    readyTime += transferCosts.get(parent).get(task);
                }
                minReadyTime = Math.max(minReadyTime, readyTime);
            }

            finishTime = findFinishTime(task, vm, minReadyTime, false);

            if (finishTime < earliestFinishTime) {
                bestReadyTime = minReadyTime;
                earliestFinishTime = finishTime;
                chosenVM = vm;
                chosenVmIndex = vmIndex;
            }
        }

        findFinishTime(task, chosenVM, bestReadyTime, true);
        earliestFinishTimes.put(task, earliestFinishTime);

        position[task.getId()] = chosenVmIndex;
    }

    /**
     * Finds the best time slot available to minimize the finish time of the
     * given task in the vm with the constraint of not scheduling it before
     * readyTime. If occupySlot is true, reserves the time slot in the schedule.
     *
     * @param task The task to have the time slot reserved
     * @param vm The vm that will execute the task
     * @param readyTime The first moment that the task is available to be
     * scheduled
     * @param occupySlot If true, reserves the time slot in the schedule.
     * @return The minimal finish time of the task in the vmn
     * yls190312 occupySlot=false 只是寻找最早完成时间;=true,找到最早完成时间，并将这段时间加入调度队列，等待调度
     */
    private double findFinishTime(Task task, VM vm, double readyTime,
            boolean occupySlot) {
        List<Event> sched = schedules.get(vm);
        double computationCost = computationCosts.get(task).get(vm);
        double start, finish;
        int pos;

//        //无插空
//        if (sched.isEmpty()) {
//            if (occupySlot) {
//                sched.add(new Event(readyTime, readyTime + computationCost));
//            }
//            return readyTime + computationCost;
//        }
//        else {
//        	start = Math.max(readyTime, sched.get(sched.size()-1).finish);
//        	if (occupySlot) {
//                sched.add(new Event(start, start + computationCost));
//            }
//            return start + computationCost;
//        }
        
        //有插空
        //VM上没有任务，放yls190312
        if (sched.isEmpty()) {
            if (occupySlot) {
                sched.add(new Event(readyTime, readyTime + computationCost));
            }
            return readyTime + computationCost;
        }

        //VM有一个任务，判断当前任务放在其前面还是后面 yls190312
        if (sched.size() == 1) {
            if (readyTime >= sched.get(0).finish) {
                pos = 1;
                start = readyTime;
            } else if (readyTime + computationCost <= sched.get(0).start) {
                pos = 0;
                start = readyTime;
            } else {
                pos = 1;
                start = sched.get(0).finish;
            }

            if (occupySlot) {
                sched.add(pos, new Event(start, start + computationCost));
            }
            return start + computationCost;
        }

        //VM有>=2的任务，从后向前找判断每两个任务之间是否可以放下任务，否则放到最后一个任务的后面 yls190312
        // Trivial case: Start after the latest task scheduled
        start = Math.max(readyTime, sched.get(sched.size() - 1).finish);
        finish = start + computationCost;
        int i = sched.size() - 1;
        int j = sched.size() - 2;
        pos = i + 1;
        while (j >= 0) {
            Event current = sched.get(i);
            Event previous = sched.get(j);

            if (readyTime > previous.finish) {
                if (readyTime + computationCost <= current.start) {
                    start = readyTime;
                    finish = readyTime + computationCost;
                  //pos=i;少个这个的赋值yls190312
                }
                
                break;
            }
            if (previous.finish + computationCost <= current.start) {
                start = previous.finish;
                finish = previous.finish + computationCost;
                pos = i;
            }
            i--;
            j--;
        }

        if (readyTime + computationCost <= sched.get(0).start) {
            pos = 0;
            start = readyTime;

            if (occupySlot) {
                sched.add(pos, new Event(start, start + computationCost));
            }
            return start + computationCost;
        }
        if (occupySlot) {
            sched.add(pos, new Event(start, finish));
        }
        return finish;
    }

}