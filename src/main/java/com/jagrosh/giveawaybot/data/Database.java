/*
 * Copyright 2022 John Grosh (john.a.grosh@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.giveawaybot.data;

import com.jagrosh.giveawaybot.entities.PremiumLevel;
import com.jagrosh.interactions.entities.User;
import java.awt.Color;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class Database
{
    private final EntityManagerFactory emf;
    private final EntityManager em;
    // temporary collections, todo database stuff
    //private final HashMap<Long,Giveaway> giveaways = new HashMap<>();
    //private final HashMap<Long,GuildSettings> settings = new HashMap<>();
    //private final HashMap<Long,GiveawayEntries> entries = new HashMap<>();
    //private final HashMap<Long,CachedUser> users = new HashMap<>();
    
    public Database(String host, String user, String pass)
    {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("javax.persistence.jdbc.user", user);
        properties.put("javax.persistence.jdbc.password", pass);
        emf = Persistence.createEntityManagerFactory(host, properties);
        em = emf.createEntityManager();
        em.getMetamodel().managedType(CachedUser.class);
        em.getMetamodel().managedType(Giveaway.class);
        em.getMetamodel().managedType(GiveawayEntries.class);
        em.getMetamodel().managedType(GuildSettings.class);
        em.getMetamodel().managedType(PremiumStatus.class);
    }
    
    // guild settings
    public GuildSettings getSettings(long guildId)
    {
        GuildSettings gs = em.find(GuildSettings.class, guildId);
        return gs == null ? new GuildSettings(guildId) : gs;
    }
    
    public void setGuildColor(long guildId, Color color)
    {
        GuildSettings gs = em.find(GuildSettings.class, guildId);
        em.getTransaction().begin();
        if(gs == null)
        {
            gs = new GuildSettings();
            gs.setGuildId(guildId);
            em.persist(gs);
        }
        gs.setColor(color);
        em.getTransaction().commit();
    }
    
    public void setGuildManager(long guildId, long roleId)
    {
        GuildSettings gs = em.find(GuildSettings.class, guildId);
        em.getTransaction().begin();
        if(gs == null)
        {
            gs = new GuildSettings();
            gs.setGuildId(guildId);
            em.persist(gs);
        }
        gs.setManagerRoleId(roleId);
        em.getTransaction().commit();
    }
    
    
    // giveaways
    public Giveaway getGiveaway(long id)
    {
        return em.find(Giveaway.class, id);
    }
    
    public List<Giveaway> getGiveawaysByGuild(long guildId)
    {
        return em.createNamedQuery("Giveaway.getAllFromGuild", Giveaway.class).setParameter("guildId", guildId).getResultList();
    }
    
    public List<Giveaway> getGiveawaysByChannel(long channelId)
    {
        return em.createNamedQuery("Giveaway.getAllFromChannel", Giveaway.class).setParameter("channelId", channelId).getResultList();
    }
    
    public long countGiveawaysByChannel(long channelId)
    {
        return em.createNamedQuery("Giveaway.countAllFromChannel", Long.class).setParameter("channelId", channelId).getSingleResult();
    }
    
    public long countGiveawaysByGuild(long guildId)
    {
        return em.createNamedQuery("Giveaway.countAllFromGuild", Long.class).setParameter("guildId", guildId).getSingleResult();
    }
    
    public long countAllGiveaways()
    {
        return em.createNamedQuery("Giveaway.countAll", Long.class).getSingleResult();
    }
    
    public List<Giveaway> getGiveawaysEndingBefore(Instant time)
    {
        return em.createNamedQuery("Giveaway.getAllEndingBefore", Giveaway.class).setParameter("endTime", time.getEpochSecond()).getResultList();
    }
    
    public void createGiveaway(Giveaway giveaway)
    {
        em.getTransaction().begin();
        em.persist(giveaway);
        em.getTransaction().commit();
    }
    
    public void removeGiveaway(long id)
    {
        Giveaway g = em.find(Giveaway.class, id);
        if(g != null)
        {
            em.getTransaction().begin();
            em.remove(g);
            em.getTransaction().commit();
        }
        GiveawayEntries ge = em.find(GiveawayEntries.class, id);
        if(ge != null)
        {
            em.getTransaction().begin();
            em.remove(ge);
            em.getTransaction().commit();
        }
    }
    
    
    // entries
    public void addEntry(long giveawayId, User user)
    {
        // update entries
        GiveawayEntries ge = em.find(GiveawayEntries.class, giveawayId);
        em.getTransaction().begin();
        
        if(ge == null)
        {
            ge = new GiveawayEntries();
            ge.setGiveawayId(giveawayId);
            em.persist(ge);
        }
        ge.addUser(user.getIdLong());
        
        
        // update cached user
        CachedUser u = em.find(CachedUser.class, user.getIdLong());
        if(u == null)
        {
            u = new CachedUser();
            u.setId(user.getIdLong());
            em.persist(u);
        }
        u.setUsername(user.getUsername());
        u.setDiscriminator(user.getDiscriminator());
        u.setAvatar(user.getAvatar());
        
        em.getTransaction().commit();
    }
    
    public List<CachedUser> getEntries(long giveawayId)
    {
        GiveawayEntries ge = em.find(GiveawayEntries.class, giveawayId);
        if(ge == null)
            return Collections.emptyList();
        return ge.getUsers().stream()
                .map(u -> em.find(CachedUser.class, u))
                .collect(Collectors.toList());
    }
    
    public int getEntryCount(long giveawayId)
    {
        return getEntries(giveawayId).size();
    }
    
    
    // premium
    public PremiumLevel getPremiumLevel(long userId)
    {
        PremiumStatus ps = em.find(PremiumStatus.class, userId);
        return ps == null ? PremiumLevel.NONE : ps.getPremium();
    }
}