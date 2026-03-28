package com.blog.tools;


import com.blog.entity.Shop;
import com.blog.service.IShopService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ShopTool {

    @Autowired
    private IShopService shopService;

    //1.工具方法: 查询商家信息
    @Tool("根据商家名称查询商家信息")
    public Shop findShop(@P("商家名称") String shopName) {
        return shopService.findShop(shopName);
    }

}
