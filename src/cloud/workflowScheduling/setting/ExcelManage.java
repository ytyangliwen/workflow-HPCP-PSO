package cloud.workflowScheduling.setting;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;


/**
 * @Description: 
 * @author 21  * @date 创建时间：2016年12月8日下午2:38:47
 * @version 1.0
 */
public class ExcelManage {
     
	HSSFWorkbook workbook = null; 
	
	/**
	 * 删除存在的Excel
	 * @param filePath 表格的文件路径
	 * 190918YLW
	 */
	public static void clearExecl(String filePath) {
        ExcelManage em = new ExcelManage();  
        //判断该名称的文件是否存在  
        boolean fileFlag = em.fileExist(filePath);        
        if(fileFlag){
        	em.deleteExcel(filePath);
        }  
	}
	/**
	 * 创建空的Excel和工作表sheet
	 * @param filePath Excel的文件路径
	 * @param sheetName 工作表的名称
	 */
	public static ExcelManage initExecl(String filePath, String sheetName) {
        ExcelManage em = new ExcelManage();  
        //判断该名称的文件是否存在  
        boolean fileFlag = em.fileExist(filePath);        
        if(!fileFlag){
           em.createExcel(filePath,sheetName);
        }  
        //判断该名称的Sheet是否存在  
        boolean sheetFlag = em.sheetExist(filePath,sheetName);
        //如果该名称的Sheet不存在，则新建一个新的Sheet
        if(!sheetFlag){
           try {
               em.createSheet(filePath,sheetName);
           } catch (FileNotFoundException e) {
               e.printStackTrace();
           } catch (IOException e) {
               e.printStackTrace();
           }
        }
		return em; 
	}
	
	/**
	 * 写入Excel
	 * @param filePath
	 * @param sheetName
	 * @param constraintTitle 约束程度
	 * @param methodTitle 使用的方法名称
	 * @param index 当前方法的序号
	 * @param solution 写入的解
	 */
    public void writeToExcel(String filePath, String sheetName, 
    		double constraintTitle, double deadline, String methodTitle, int index, Solution solution){  
        //创建workbook  
        File file = new File(filePath);  
        try {  
            workbook = new HSSFWorkbook(new FileInputStream(file));  
        } catch (FileNotFoundException e) {  
            e.printStackTrace();  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
        FileOutputStream out = null;  
        HSSFSheet sheet = workbook.getSheet(sheetName);  
        // 获取表格的总行数  
        int rowCount = sheet.getLastRowNum() + 1; // 需要加一  
        Row row;
        Cell cell;
        try {  
        	if (index == 0) { //方法序号为0时，写入约束title和deadline
        		row = sheet.createRow(rowCount + 1);     //最新要添加的一行
        		cell = row.createCell(0); //添加一列
        		cell.setCellValue("约束程度" + String.valueOf(constraintTitle) + " [Deadline=" + String.valueOf(deadline) + "]");
        		
            	rowCount = sheet.getLastRowNum() + 1;
        	} 
        	
        	//打印当前方法和当前方法求得的cost和makespan
    		row = sheet.createRow(rowCount);     //最新要添加的一行
    		cell = row.createCell(0);  
        	if(solution == null) {
        		cell.setCellValue(methodTitle + "的解" + "  " + "sol=null");
        	}
        	else {
        		cell.setCellValue(methodTitle + "的解" + "  "
        				+ "[cost=" + String.format("%.3f", solution.calcCost()) + "]" + ", makespan=" + String.format("%.3f", solution.calcMakespan()) + "]");
        		
	        	//写入使用的虚拟机
	        	rowCount++;
	        	HashMap<Integer, Integer> vmIdVsRow = new HashMap<Integer, Integer>();
	        	int vmNum = 0;
	        	for(VM vm : solution.keySet()){
	        		vmIdVsRow.put(vm.getId(), rowCount);
	        		row = sheet.createRow(rowCount++);     //最新要添加的一行
	        		cell = row.createCell(0);  
	            	cell.setCellValue("VM" + vmNum + "[id=" + vm.getId() + ",type=" + vm.getType() + "]");
	            	vmNum++;
	    		}     
	        	//按照task的开始时间，依次写入task指派的vm所在的行
	        	Map<Task, Allocation> map = solution.getRevMapping();
	        	Set<Map.Entry<Task, Allocation>> entrySet = map.entrySet();
	            //////借助list实现hashMap排序//////
	            //注意 ArrayList<>() 括号里要传入map.entrySet()
	            List<Map.Entry<Task, Allocation>> list = new ArrayList<>(map.entrySet());
	            Collections.sort(list, new Comparator<Map.Entry<Task, Allocation>>(){
	                @Override
	                public int compare(Map.Entry<Task, Allocation> o1, Map.Entry<Task, Allocation> o2)
	                {
	                    //按照value值，重小到大排序
//	                    return (int)(o1.getValue().getStartTime() - o2.getValue().getStartTime());
	                	return Double.compare(o1.getValue().getStartTime(), o2.getValue().getStartTime());
	                }
	            });
	            int cellCount = 1;
	            double lastStartTime = 0.0;
	            double lastFinishTime = 0.0;
	            for (Map.Entry s : list) {
	            	Task t = (Task)s.getKey();
	            	Allocation allo = (Allocation)s.getValue();
	            	rowCount = vmIdVsRow.get(allo.getVM().getId());
	            	row = sheet.getRow(rowCount);     //最新要添加的一行
	            	if(allo.getStartTime() != lastStartTime)
	            		cellCount++;
	            	else if(allo.getFinishTime() == 0 || lastFinishTime == 0)
	            		cellCount++;
	        		cell = row.createCell(cellCount);  
	            	cell.setCellValue("t" + t.getId() + "[" + 
	            			String.format("%.3f", allo.getStartTime()) + ", " + String.format("%.3f", allo.getFinishTime()) + "]");
	            	lastStartTime = allo.getStartTime();
	            	lastFinishTime = allo.getFinishTime();
	            } 
        	}
        	
        	//设置自动列宽
        	for(int i = 0; i < 1000; i++)
            	sheet.autoSizeColumn(i);
        	
            out = new FileOutputStream(filePath);  
            workbook.write(out); 
        } catch (Exception e) {  
            e.printStackTrace();  
        } finally {    
            try {    
                out.close();    
            } catch (IOException e) {    
                e.printStackTrace();  
            }    
        }    
    }

    /** 
     * 往excel中写入. 
     * @param filePath    文件路径 
     * @param sheetName  表格索引 
     * @param object 
     */  
    public void writeToExcel(String filePath,String sheetName,int parentId, int childId){  
        //创建workbook  
        File file = new File(filePath);  
        try {  
            workbook = new HSSFWorkbook(new FileInputStream(file));  
        } catch (FileNotFoundException e) {  
            e.printStackTrace();  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
        FileOutputStream out = null;  
        HSSFSheet sheet = workbook.getSheet(sheetName);  
        // 获取表格的总行数  
        int rowCount = sheet.getLastRowNum() + 1; // 需要加一  
        try {  
            Cell cell = sheet.createRow(rowCount).createCell(0);  
        	cell.setCellValue("sol违反依赖, parentId=" + parentId + ", childId=" + childId);
     
            out = new FileOutputStream(filePath);  
            workbook.write(out);  
        } catch (Exception e) {  
            e.printStackTrace();  
        } finally {    
            try {    
                out.close();    
            } catch (IOException e) {    
                e.printStackTrace();  
            }    
        }    
    } 
    
    /** 
     * 判断文件是否存在
     * @param filePath  文件路径 
     * @return 
     */  
    public boolean fileExist(String filePath){  
         boolean flag = false;  
         File file = new File(filePath);  
         flag = file.exists();  
         return flag;  
    }  
    
    /** 
     * 判断文件的sheet是否存在
     * @param filePath   文件路径 
     * @param sheetName  表格索引名 
     * @return 
     */  
    public boolean sheetExist(String filePath,String sheetName){  
         boolean flag = false;  
         File file = new File(filePath);  
         if(file.exists()){    //文件存在  
            //创建workbook  
             try {  
                workbook = new HSSFWorkbook(new FileInputStream(file));  
                //添加Worksheet（不添加sheet时生成的xls文件打开时会报错)  
                HSSFSheet sheet = workbook.getSheet(sheetName);    
                if(sheet!=null)  
                    flag = true;  
            } catch (Exception e) {  
                e.printStackTrace();  
            }                 
         }else{    //文件不存在  
             flag = false;  
         }            
         return flag;  
    }
    /** 
     * 创建新Sheet并写入第一行数据
     * @param filePath  excel的路径 
     * @param sheetName 要创建的表格索引 
     * @param titleRow excel的第一行即表格头 
     * @throws IOException 
     * @throws FileNotFoundException 
     */  
    public void createSheet(String filePath,String sheetName) throws FileNotFoundException, IOException{ 
        FileOutputStream out = null;         
        File excel = new File(filePath);  // 读取文件
        FileInputStream in = new FileInputStream(excel); // 转换为流
        workbook = new HSSFWorkbook(in); // 加载excel的 工作目录       
                          
        workbook.createSheet(sheetName); // 添加一个新的sheet  
        //添加表头  
//        Row row = workbook.getSheet(sheetName).createRow(0);    //创建第一行            
        try {              
             
//           Cell cell = row.createCell(0);  
//           cell.setCellValue(titleRow);   
           
           out = new FileOutputStream(filePath);  
            workbook.write(out);
       }catch (Exception e) {  
           e.printStackTrace();  
       }finally {    
           try {    
               out.close();    
           } catch (IOException e) {    
               e.printStackTrace();  
           }    
       }             
    }
    /** 
     * 创建新excel. 
     * @param filePath  excel的路径 
     * @param sheetName 要创建的表格索引 
     * @param titleRow excel的第一行即表格头 
     */  
    public void createExcel(String filePath,String sheetName){  
        //创建workbook  
        workbook = new HSSFWorkbook();  
        //添加Worksheet（不添加sheet时生成的xls文件打开时会报错)  
//        workbook.createSheet(sheetName);    
        //新建文件  
        FileOutputStream out = null;  
        try {  
//            //添加表头  
//            Row row = workbook.getSheet(sheetName).createRow(0);    //创建第一行     
//            Cell cell = row.createCell(0);  //创建第一行的第一列
//            cell.setCellValue(titleRow);   //设置第一行第一列的值
              
            out = new FileOutputStream(filePath);  
            workbook.write(out);  
        } catch (Exception e) {  
            e.printStackTrace();  
        } finally {    
            try {    
                out.close();    
            } catch (IOException e) {    
                e.printStackTrace();  
            }    
        }    
    }  
    /** 
     * 删除文件. 
     * @param filePath  文件路径 
     */  
    public boolean deleteExcel(String filePath){  
        boolean flag = false;  
        File file = new File(filePath);  
        // 判断目录或文件是否存在    
        if (!file.exists()) {  
            return flag;    
        } else {    
            // 判断是否为文件    
            if (file.isFile()) {  // 为文件时调用删除文件方法    
                file.delete();  
                flag = true;  
            }   
        }  
        return flag;  
    }   
    
}
