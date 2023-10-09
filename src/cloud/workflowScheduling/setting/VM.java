package cloud.workflowScheduling.setting;

import java.io.Serializable;

// virtual machine, i.e., cloud service resource
public class VM {
	public static final double LAUNCH_TIME = 0;	
	public static final long NETWORK_SPEED = 20 * 1024*1024;
	
	//L-ACO中的
	public static final int TYPE_NO = 9;
	public static final double[] SPEEDS = {1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5};
	public static final double[] UNIT_COSTS = {0.12, 0.195, 0.28, 0.375, 0.48, 0.595, 0.72, 0.855, 1};
	public static final double INTERVAL = 3600;	//one hour, billing interval

	public static final int FASTEST = 8;
	public static final int SLOWEST = 0;
	
	
	//文章中常用的
//	public static final int TYPE_NO = 6;
//	public static final double[] SPEEDS = {4400, 8800, 17600, 35200, 57200, 114400};
//	public static final double[] UNIT_COSTS = {0.06, 0.12, 0.24, 0.48, 0.5, 1};
//	public static final double INTERVAL = 3600;	//one hour, billing interval
//
//	public static final int FASTEST = 5;
//	public static final int SLOWEST = 0;
	
	private static int internalId = 0;
	public static void resetInternalId(){	//called by the constructor of Solution
		internalId = 0;
	}
	public static void setInternalId(int startId){
		internalId = startId;
	}
	
	private int id;
	private int type; 

	public VM(int type){
		this.type = type;
		this.id = internalId++;
	}
	
	//------------------------getters && setters---------------------------
	public void setType(int type) {		//can only be invoked in the same package, e.g., Solution
		this.type = type;
	}
	public void setId(int id) {
		this.id = id;
	}
	public double getSpeed(){		return SPEEDS[type];	}
	public double getUnitCost(){		return UNIT_COSTS[type];	}
	public int getId() {		return id;	}
	public int getType() {		return type;	}
	
	//-------------------------------------overrides--------------------------------
	public String toString() {
		return "VM [id=" + id + ", type=" + type + "]";
	}
}