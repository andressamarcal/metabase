
import { addLocale, useLocale } from "c-3po";
import { I18NApi } from "metabase/services";
import MetabaseSettings from "metabase/lib/settings";

export async function loadLocalization(locale) {
    // load and parse the locale
    const translationsObject = await I18NApi.locale({ locale });
    setLocalization(translationsObject);
}

export function setLocalization(translationsObject) {
    const locale = translationsObject.headers.language;

    try {
      translationsObject.translations[""]["Metabase"].msgstr = [MetabaseSettings.applicationName()]
    } catch (e) {
      console.error("Couldn't set application name", e)
    }

    // add and set locale with C-3PO
    addLocale(locale, translationsObject);
    useLocale(locale);
}
