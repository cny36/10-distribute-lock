package com.cny.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author : chennengyuan
 */
@Slf4j
public class ZkDistirbuteLock implements Watcher, AutoCloseable {

    private ZooKeeper zooKeeper;

    private String znode;

    public ZkDistirbuteLock() throws IOException {
        this.zooKeeper = new ZooKeeper("192.168.247.5:2181", 630000, this);
    }

    //上锁
    public boolean lock(String businessCode) {
        try {
            //1.创建业务根节点
            Stat rootNodeStat = zooKeeper.exists("/" + businessCode, false);
            if (rootNodeStat == null) {
                zooKeeper.create("/" + businessCode, businessCode.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
            //2.根据根节点创建下面的临时顺序节点
            znode = zooKeeper.create("/" + businessCode + "/" + businessCode, businessCode.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            //2.1判断是否为第一个节点
            List<String> children = zooKeeper.getChildren("/" + businessCode, false);
            Collections.sort(children);
            String firstNode = children.get(0);
            if (znode.endsWith(firstNode)) {
                log.info("为当前第一个顺序节点，获取到锁成功 - {}", Thread.currentThread().getName());
                return true;
            }
            //2.2不是第一个节点 监听上一个节点 进入阻塞等待
            String preNode = firstNode;
            for (int i = 0; i < children.size(); i++) {
                if (znode.endsWith(children.get(i))) {
                    zooKeeper.exists("/" + businessCode + "/" + preNode, true);
                    break;
                } else {
                    preNode = children.get(i);
                }
            }
            log.info("不是当前第一个子节点，进入监听等待的状态 - {}", Thread.currentThread().getName());
            synchronized (this) {
                wait();
            }

            //当被唤醒的时候，当前客户端创建的节点就是第一个节点
            log.info("前一个节点已经删除，当前节点变为第一个节点，获取锁成功 - {}", Thread.currentThread().getName());
            return true;

        } catch (Exception e) {
            log.info(e.getMessage());
            return false;
        }

    }

    @Override
    public void process(WatchedEvent watchedEvent) {
        if (watchedEvent.getType() == Event.EventType.NodeDeleted) {
            synchronized (this) {
                notify();
            }
        }

    }

    @Override
    public void close() throws Exception {
        zooKeeper.delete(znode, -1);
        zooKeeper.close();
        log.info("删除锁 - {}", Thread.currentThread().getName());
    }
}
