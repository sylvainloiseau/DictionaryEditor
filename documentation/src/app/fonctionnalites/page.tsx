"use client";

import Link from "next/link";
import { useLanguage } from "@/context/LanguageContext";

export default function FonctionnalitesPage() {
  const { t } = useLanguage();

  return (
    <div className="space-y-6 sm:space-y-8">
      <div>
        <Link href="/" className="mb-4 inline-block text-sm text-gray-600 hover:text-[#4c6f76]">
          {t.common.back}
        </Link>
        <h1 className="text-2xl font-bold text-[#4c6f76] sm:text-3xl">{t.fonctionnalites.title}</h1>
        <p className="mt-2 text-gray-600">{t.fonctionnalites.subtitle}</p>
      </div>

      <section className="space-y-4">
        <h2 className="text-xl font-semibold text-gray-800">{t.fonctionnalites.navTitle}</h2>
        <ul className="ml-4 list-disc list-inside space-y-2 text-gray-600">
          {t.fonctionnalites.navItems.map((item, i) => (
            <li key={i}>{item}</li>
          ))}
        </ul>
      </section>

      <section className="space-y-4">
        <h2 className="text-xl font-semibold text-gray-800">{t.fonctionnalites.tablesTitle}</h2>
        <ul className="ml-4 list-disc list-inside space-y-2 text-gray-600">
          {t.fonctionnalites.tablesItems.map((item, i) => (
            <li key={i}>{item}</li>
          ))}
        </ul>
      </section>

      <section className="space-y-4">
        <h2 className="text-xl font-semibold text-gray-800">{t.fonctionnalites.categoryTitle}</h2>
        <p className="leading-relaxed text-gray-600">{t.fonctionnalites.categoryDesc}</p>
      </section>

      <section className="space-y-4">
        <h2 className="text-xl font-semibold text-gray-800">{t.fonctionnalites.configTitle}</h2>
        <ul className="ml-4 list-disc list-inside space-y-2 text-gray-600">
          {t.fonctionnalites.configItems.map((item, i) => (
            <li key={i}>{item}</li>
          ))}
        </ul>
      </section>

      <section className="space-y-4">
        <h2 className="text-xl font-semibold text-gray-800">{t.fonctionnalites.toolsTitle}</h2>
        <ul className="ml-4 list-disc list-inside space-y-2 text-gray-600">
          {t.fonctionnalites.toolsItems.map((item, i) => (
            <li key={i}>{item}</li>
          ))}
        </ul>
      </section>
    </div>
  );
}
