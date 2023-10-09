package cloud.workflowScheduling.setting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

public class DeepCopyUtil {
	
    public static <T> List<T> copy(List<T> srcs) throws IOException, ClassNotFoundException {  
		List<T> dests = new ArrayList<T>();  
		for(T src:srcs ){
			   
			T dest =  copy(src) ;
			dests.add(dest);
		} 
	    return dests;          
	}  
    
    public static <T> T copy(T src) throws IOException, ClassNotFoundException {  
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();  
        ObjectOutputStream out;
		
		out = new ObjectOutputStream(byteOut);
		out.writeObject(src);  

        ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());  
        ObjectInputStream in = new ObjectInputStream(byteIn);  
        @SuppressWarnings("unchecked")  
        T dest = (T) in.readObject();  
        return dest;  
    }  
    
}