package com.pistonpunk.gigahack26.service;

import java.util.List;

public final class RegionTaxonomy {

    private RegionTaxonomy() {
    }

    public record Definition(String key, String name, String parentKey, String countries) {
    }

    public static List<Definition> definitions() {
        return List.of(
                continent("europe", "Europe"),
                region("uk-ireland", "UK & Ireland", "europe", "GB,IE,IM,JE,GG"),
                region("dach", "DACH", "europe", "DE,AT,CH,LI"),
                region("benelux", "Benelux", "europe", "BE,NL,LU"),
                region("nordics", "Nordics", "europe", "SE,NO,DK,FI,IS"),
                region("france", "France", "europe", "FR,MC"),
                region("southern-europe", "Southern Europe", "europe", "ES,PT,IT,GR,MT,CY"),
                region("central-europe", "Central Europe", "europe", "PL,CZ,SK,HU,SI,HR"),
                region("baltics", "Baltics", "europe", "EE,LV,LT"),
                region("southeast-europe", "South-East Europe", "europe", "RO,BG,RS,BA,MK,AL,ME,MD,XK"),
                region("eastern-europe", "Eastern Europe", "europe", "UA,BY,GE,AM,AZ"),

                continent("north-america", "North America"),
                region("united-states", "United States", "north-america", "US"),
                region("canada", "Canada", "north-america", "CA"),

                continent("latin-america", "Latin America"),
                region("brazil", "Brazil", "latin-america", "BR"),
                region("southern-cone", "Southern Cone", "latin-america", "AR,CL,UY,PY"),
                region("andean", "Andean", "latin-america", "CO,PE,EC,VE,BO"),
                region("central-america-caribbean", "Central America & Caribbean", "latin-america",
                        "MX,CR,PA,GT,DO,PR,SV,HN,NI"),

                continent("middle-east", "Middle East"),
                region("gulf", "Gulf", "middle-east", "AE,SA,QA,KW,BH,OM"),
                region("levant", "Levant", "middle-east", "IL,JO,LB,IQ"),
                region("turkiye", "Türkiye", "middle-east", "TR"),

                continent("africa", "Africa"),
                region("north-africa", "North Africa", "africa", "EG,MA,DZ,TN,LY"),
                region("west-africa", "West Africa", "africa", "NG,GH,CI,SN,CM"),
                region("east-africa", "East Africa", "africa", "KE,TZ,UG,ET,RW,MU"),
                region("southern-africa", "Southern Africa", "africa", "ZA,NA,BW,ZW,MZ,AO"),

                continent("asia-pacific", "Asia & Pacific"),
                region("south-asia", "South Asia", "asia-pacific", "IN,PK,BD,LK,NP"),
                region("southeast-asia", "South-East Asia", "asia-pacific", "SG,MY,TH,VN,ID,PH,KH,MM"),
                region("greater-china", "Greater China", "asia-pacific", "CN,HK,TW,MO"),
                region("north-asia", "Japan & Korea", "asia-pacific", "JP,KR"),
                region("oceania", "Oceania", "asia-pacific", "AU,NZ"));
    }

    private static Definition continent(String key, String name) {
        return new Definition(key, name, null, null);
    }

    private static Definition region(String key, String name, String parent, String countries) {
        return new Definition(key, name, parent, countries);
    }
}
