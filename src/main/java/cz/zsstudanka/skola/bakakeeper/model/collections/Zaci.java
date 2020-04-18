package cz.zsstudanka.skola.bakakeeper.model.collections;

import cz.zsstudanka.skola.bakakeeper.model.interfaces.IKolekceAD;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IUzivatelAD;
import cz.zsstudanka.skola.bakakeeper.model.entities.Zak;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.text.RuleBasedCollator;
import java.util.*;

/**
 * Žáci - dynamická kolekce.
 *
 *  Žák je objekt uživatel Active Directory.
 *  Žák je záznam na SQL Serveru.
 *
 * @author Jan Hladěna
 * @deprecated
 */
public class Zaci implements IKolekceAD {

    private Iterator iterator = null;

    /** primární klíč v kolekci je INTERN_KOD */
    private Map<String, Zak> zaci = null;

    public Zaci() {
        this.zaci = new LinkedHashMap<String, Zak>();
    }

    /**
     * Přidání hotového žáka do kolekce.
     *
     * @param zak
     */
    public void add(Zak zak) {
        // TODO - to není AD login :-o
        this.zaci.put(zak.getIntern_kod(), zak);
    }

    /**
     * Odebrání žáka z kolekce.
     *
     * @param zak instance řáka
     */
    public void remove(Zak zak) {
        if (zaci.containsValue(zak)) {
            zaci.remove(zak.getADLogin());
        }
    }

    /**
     * Odebrání žáka z kolekce.
     *
     * @param login login žáka v AD
     */
    public void remove(String login) {
        if (zaci.containsKey(login)) {
            zaci.remove(login);
        }
    }

    /**
     * Instance žáka podle loginu/primárního klíče kolekce.
     *
     * @param login
     * @return instance žáka, nebo null
     */
    public Zak get(String login) {
        if (zaci.containsKey(login)) {
            return zaci.get(login);
        }

        return null;
    }

    /**
     * Počet žáků v dané kolekci.
     *
     * @return počet žáků
     */
    public Integer getCount() {
        if (zaci == null) {
            return 0;
        }

        return zaci.size();
    }

    /**
     * Instance žáka podle položky INTERN_KOD v Bakalářích.
     *
     * @param intern_kod
     * @return instance žáka, nebo null
     */
    public Zak getByInternKod(String intern_kod) {
        Iterator seznam = this.zaci.entrySet().iterator();
        while (seznam.hasNext()) {
            Zak tmpZak = this.zaci.get(seznam.next());
            return (tmpZak.getIntern_kod().equals(intern_kod)) ? tmpZak : null;
        }

        return null;
    }

    /**
     * Instance žáka podle e-mailové adresy v Bakalářích.
     *
     * @param mail e-mailová adresa
     * @return instance žáka, nebo null
     */
    public Zak getByEmail(String mail) {
        // TODO
        return null;
    }

    /**
     * Instance žáka podle e-mailové adresy v Active Directory.
     *
     * @param aDEmail školní e-mailová adresa
     * @return instance žáka, nebo null
     */
    public Zak getByADEmail(String aDEmail) {
        Iterator seznam = this.zaci.entrySet().iterator();
        while (seznam.hasNext()) {
            Zak tmpZak = this.zaci.get(seznam.next());

            if (tmpZak.getADEmail().equals(aDEmail)) {
                return tmpZak;
            }
        }

        return null;
    }

    /**
     * Seřazení mapy žáků podle příjmení.
     *
     * @return seřazaná mapa žáků
     * @deprecated
     */
    private HashMap<String, Zak> sortByName() {
        List<Map.Entry<String, Zak>> list = new LinkedList<Map.Entry<String, Zak>>(this.zaci.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<String, Zak>>() {
            @Override
            public int compare(Map.Entry<String, Zak> z1, Map.Entry<String, Zak> z2) {

                RuleBasedCollator collator = null;

                try {
                    collator = new RuleBasedCollator(Settings.CZ_COL);
                    return collator.compare(z1.getValue().getPrijmeni(), z2.getValue().getPrijmeni());
                } catch (Exception e) { }

                return (z1.getValue().getPrijmeni()).compareTo(z2.getValue().getPrijmeni());
            }
        });

        HashMap<String, Zak> tmpZaci = new LinkedHashMap<String, Zak>();
        for (Map.Entry<String, Zak> zak : list) {
            tmpZaci.put(zak.getKey(), zak.getValue());
        }

        return tmpZaci;
    }

    /**
     * Iterátor nad žáky.
     *
     * @return instance iterátoru
     */
    @Override
    public Iterator iterator() {

        if (this.iterator == null) {
            //this.iterator = sortByName().entrySet().iterator();
            this.iterator = this.zaci.entrySet().iterator();
        }

        return iterator;
    }

    public void resetIterator() {
        //this.iterator = sortByName().entrySet().iterator();
        this.iterator = this.zaci.entrySet().iterator();
    }

    /**
     * Další žák v pořadí.
     *
     * @return následující instance žáka v iterátoru
     */
    public Zak next() {

        if (this.iterator == null) {
            resetIterator();
        }

        Map.Entry zakElement = (Map.Entry) iterator.next();
        return this.zaci.get(zakElement.getKey());
    }

    /**
     * Vyhledání žáka v kolekci podle sAMAccountName.
     *
     * @param ad_login kolizní řetězec
     * @return instance žáka nebo null
     */
    @Override
    public IUzivatelAD findCollisions(String ad_login) {

        Iterator seznam = this.zaci.entrySet().iterator();
        while (seznam.hasNext()) {
            Zak tmpZak = this.zaci.get(seznam.next());
            return (tmpZak.getADLogin().equals(ad_login)) ? tmpZak : null;
        }

        return null;
    }
}
