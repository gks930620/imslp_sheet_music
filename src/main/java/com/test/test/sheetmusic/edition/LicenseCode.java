package com.test.test.sheetmusic.edition;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * IMSLP 저작권 표기 정규화 코드 (01_ERD §4, 00 조사 §1-4).
 * 표기 철자가 버전마다 달라(Attribution-ShareAlike / Attribution Share Alike …) 원문을 눌러 붙인 뒤 판정한다.
 */
public enum LicenseCode {
    PD, CC0, CC_BY, CC_BY_SA, CC_BY_NC, CC_BY_NC_SA, CC_BY_NC_ND, OTHER;

    /** 우리가 파일을 받아도 되는 표기 (기획 01 §9-1). */
    private static final Set<LicenseCode> REDISTRIBUTABLE = EnumSet.of(PD, CC0, CC_BY, CC_BY_SA);

    /** IMSLP 표기 원문 → 코드. null/빈 값이면 null. */
    public static LicenseCode fromText(String copyrightText) {
        if (copyrightText == null || copyrightText.isBlank()) {
            return null;
        }
        String squeezed = copyrightText.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (squeezed.contains("publicdomain")) {
            return PD;
        }
        if (squeezed.contains("creativecommonszero") || squeezed.startsWith("cc0")) {
            return CC0;
        }
        boolean attribution = squeezed.contains("attribution");
        if (!attribution) {
            return OTHER;
        }
        boolean nonCommercial = squeezed.contains("noncommercial");
        boolean noDerivatives = squeezed.contains("noderiv");
        boolean shareAlike = squeezed.contains("sharealike");
        boolean performanceRestricted = squeezed.contains("performancerestricted");
        if (performanceRestricted) {
            return OTHER;
        }
        if (nonCommercial && noDerivatives) {
            return CC_BY_NC_ND;
        }
        if (nonCommercial && shareAlike) {
            return CC_BY_NC_SA;
        }
        if (nonCommercial) {
            return CC_BY_NC;
        }
        if (noDerivatives) {
            return OTHER;
        }
        if (shareAlike) {
            return CC_BY_SA;
        }
        return CC_BY;
    }

    /** 재배포(=우리 서버가 파일을 보관·제공)가 허용되는 표기인지. */
    public static boolean isRedistributable(LicenseCode code) {
        return code != null && REDISTRIBUTABLE.contains(code);
    }
}
