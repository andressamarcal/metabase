import MetabaseSettings from "metabase/lib/settings";

import { addLocale, useLocale } from "c-3po";
import { I18NApi } from "metabase/services";

export async function loadLocale(locale) {
    // load and parse the locale
    const translationsObject = await I18NApi.locale({ locale });

    // inject the application name
    translationsObject.translations[""]["Metabase"].msgstr = [
        MetabaseSettings.applicationName()
    ];

    // add and set locale with C-3PO
    addLocale(locale, translationsObject);
    useLocale(locale);
}
