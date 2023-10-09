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
 * @author 21  * @date ����ʱ�䣺2016��12��8������2:38:47
 * @version 1.0
 */
public class ExcelManage {
     
	HSSFWorkbook workbook = null; 
	
	/**
	 * ɾ�����ڵ�Excel
	 * @param filePath �����ļ�·��
	 * 190918YLW
	 */
	public static void clearExecl(String filePath) {
        ExcelManage em = new ExcelManage();  
        //�жϸ����Ƶ��ļ��Ƿ����  
        boolean fileFlag = em.fileExist(filePath);        
        if(fileFlag){
        	em.deleteExcel(filePath);
        }  
	}
	/**
	 * �����յ�Excel�͹�����sheet
	 * @param filePath Excel���ļ�·��
	 * @param sheetName �����������
	 */
	public static ExcelManage initExecl(String filePath, String sheetName) {
        ExcelManage em = new ExcelManage();  
        //�жϸ����Ƶ��ļ��Ƿ����  
        boolean fileFlag = em.fileExist(filePath);        
        if(!fileFlag){
           em.createExcel(filePath,sheetName);
        }  
        //�жϸ����Ƶ�Sheet�Ƿ����  
        boolean sheetFlag = em.sheetExist(filePath,sheetName);
        //��������Ƶ�Sheet�����ڣ����½�һ���µ�Sheet
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
	 * д��Excel
	 * @param filePath
	 * @param sheetName
	 * @param constraintTitle Լ���̶�
	 * @param methodTitle ʹ�õķ�������
	 * @param index ��ǰ���������
	 * @param solution д��Ľ�
	 */
    public void writeToExcel(String filePath, String sheetName, 
    		double constraintTitle, double deadline, String methodTitle, int index, Solution solution){  
        //����workbook  
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
        // ��ȡ����������  
        int rowCount = sheet.getLastRowNum() + 1; // ��Ҫ��һ  
        Row row;
        Cell cell;
        try {  
        	if (index == 0) { //�������Ϊ0ʱ��д��Լ��title��deadline
        		row = sheet.createRow(rowCount + 1);     //����Ҫ��ӵ�һ��
        		cell = row.createCell(0); //���һ��
        		cell.setCellValue("Լ���̶�" + String.valueOf(constraintTitle) + " [Deadline=" + String.valueOf(deadline) + "]");
        		
            	rowCount = sheet.getLastRowNum() + 1;
        	} 
        	
        	//��ӡ��ǰ�����͵�ǰ������õ�cost��makespan
    		row = sheet.createRow(rowCount);     //����Ҫ��ӵ�һ��
    		cell = row.createCell(0);  
        	if(solution == null) {
        		cell.setCellValue(methodTitle + "�Ľ�" + "  " + "sol=null");
        	}
        	else {
        		cell.setCellValue(methodTitle + "�Ľ�" + "  "
        				+ "[cost=" + String.format("%.3f", solution.calcCost()) + "]" + ", makespan=" + String.format("%.3f", solution.calcMakespan()) + "]");
        		
	        	//д��ʹ�õ������
	        	rowCount++;
	        	HashMap<Integer, Integer> vmIdVsRow = new HashMap<Integer, Integer>();
	        	int vmNum = 0;
	        	for(VM vm : solution.keySet()){
	        		vmIdVsRow.put(vm.getId(), rowCount);
	        		row = sheet.createRow(rowCount++);     //����Ҫ��ӵ�һ��
	        		cell = row.createCell(0);  
	            	cell.setCellValue("VM" + vmNum + "[id=" + vm.getId() + ",type=" + vm.getType() + "]");
	            	vmNum++;
	    		}     
	        	//����task�Ŀ�ʼʱ�䣬����д��taskָ�ɵ�vm���ڵ���
	        	Map<Task, Allocation> map = solution.getRevMapping();
	        	Set<Map.Entry<Task, Allocation>> entrySet = map.entrySet();
	            //////����listʵ��hashMap����//////
	            //ע�� ArrayList<>() ������Ҫ����map.entrySet()
	            List<Map.Entry<Task, Allocation>> list = new ArrayList<>(map.entrySet());
	            Collections.sort(list, new Comparator<Map.Entry<Task, Allocation>>(){
	                @Override
	                public int compare(Map.Entry<Task, Allocation> o1, Map.Entry<Task, Allocation> o2)
	                {
	                    //����valueֵ����С��������
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
	            	row = sheet.getRow(rowCount);     //����Ҫ��ӵ�һ��
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
        	
        	//�����Զ��п�
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
     * ��excel��д��. 
     * @param filePath    �ļ�·�� 
     * @param sheetName  ������� 
     * @param object 
     */  
    public void writeToExcel(String filePath,String sheetName,int parentId, int childId){  
        //����workbook  
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
        // ��ȡ����������  
        int rowCount = sheet.getLastRowNum() + 1; // ��Ҫ��һ  
        try {  
            Cell cell = sheet.createRow(rowCount).createCell(0);  
        	cell.setCellValue("solΥ������, parentId=" + parentId + ", childId=" + childId);
     
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
     * �ж��ļ��Ƿ����
     * @param filePath  �ļ�·�� 
     * @return 
     */  
    public boolean fileExist(String filePath){  
         boolean flag = false;  
         File file = new File(filePath);  
         flag = file.exists();  
         return flag;  
    }  
    
    /** 
     * �ж��ļ���sheet�Ƿ����
     * @param filePath   �ļ�·�� 
     * @param sheetName  ��������� 
     * @return 
     */  
    public boolean sheetExist(String filePath,String sheetName){  
         boolean flag = false;  
         File file = new File(filePath);  
         if(file.exists()){    //�ļ�����  
            //����workbook  
             try {  
                workbook = new HSSFWorkbook(new FileInputStream(file));  
                //���Worksheet�������sheetʱ���ɵ�xls�ļ���ʱ�ᱨ��)  
                HSSFSheet sheet = workbook.getSheet(sheetName);    
                if(sheet!=null)  
                    flag = true;  
            } catch (Exception e) {  
                e.printStackTrace();  
            }                 
         }else{    //�ļ�������  
             flag = false;  
         }            
         return flag;  
    }
    /** 
     * ������Sheet��д���һ������
     * @param filePath  excel��·�� 
     * @param sheetName Ҫ�����ı������ 
     * @param titleRow excel�ĵ�һ�м����ͷ 
     * @throws IOException 
     * @throws FileNotFoundException 
     */  
    public void createSheet(String filePath,String sheetName) throws FileNotFoundException, IOException{ 
        FileOutputStream out = null;         
        File excel = new File(filePath);  // ��ȡ�ļ�
        FileInputStream in = new FileInputStream(excel); // ת��Ϊ��
        workbook = new HSSFWorkbook(in); // ����excel�� ����Ŀ¼       
                          
        workbook.createSheet(sheetName); // ���һ���µ�sheet  
        //��ӱ�ͷ  
//        Row row = workbook.getSheet(sheetName).createRow(0);    //������һ��            
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
     * ������excel. 
     * @param filePath  excel��·�� 
     * @param sheetName Ҫ�����ı������ 
     * @param titleRow excel�ĵ�һ�м����ͷ 
     */  
    public void createExcel(String filePath,String sheetName){  
        //����workbook  
        workbook = new HSSFWorkbook();  
        //���Worksheet�������sheetʱ���ɵ�xls�ļ���ʱ�ᱨ��)  
//        workbook.createSheet(sheetName);    
        //�½��ļ�  
        FileOutputStream out = null;  
        try {  
//            //��ӱ�ͷ  
//            Row row = workbook.getSheet(sheetName).createRow(0);    //������һ��     
//            Cell cell = row.createCell(0);  //������һ�еĵ�һ��
//            cell.setCellValue(titleRow);   //���õ�һ�е�һ�е�ֵ
              
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
     * ɾ���ļ�. 
     * @param filePath  �ļ�·�� 
     */  
    public boolean deleteExcel(String filePath){  
        boolean flag = false;  
        File file = new File(filePath);  
        // �ж�Ŀ¼���ļ��Ƿ����    
        if (!file.exists()) {  
            return flag;    
        } else {    
            // �ж��Ƿ�Ϊ�ļ�    
            if (file.isFile()) {  // Ϊ�ļ�ʱ����ɾ���ļ�����    
                file.delete();  
                flag = true;  
            }   
        }  
        return flag;  
    }   
    
}
