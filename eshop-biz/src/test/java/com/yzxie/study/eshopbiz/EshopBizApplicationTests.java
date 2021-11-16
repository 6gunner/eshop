package com.yzxie.study.eshopbiz;

import com.yzxie.study.eshopbiz.repository.ProductQuantityDAO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class EshopBizApplicationTests {

    @Autowired
    private ProductQuantityDAO productQuantityDAO;
    @Test
    public void contextLoads() {
    
        Long quantity = productQuantityDAO.getProductQuantity(1);
        System.out.println(quantity);
        
    }

}
