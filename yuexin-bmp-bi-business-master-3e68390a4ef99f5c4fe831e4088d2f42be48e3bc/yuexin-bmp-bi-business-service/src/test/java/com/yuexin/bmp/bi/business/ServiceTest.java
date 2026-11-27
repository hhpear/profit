package com.yuexin.bmp.bi.business;

import com.yuexin.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;


@SpringBootTest
@ExtendWith(SpringExtension.class)
@Slf4j
@ActiveProfiles("debug")
public class ServiceTest {

    @Test
    public void test() throws BusinessException {

    }

}