"""LEGI adapter on a fictional archive with the verified layout (AGENTS.md §2.6: no real text)."""

import io
import sqlite3
import tarfile
from datetime import date

import pytest

from citoyen_pipeline import legal
from citoyen_pipeline.build import write_legal_pack
from citoyen_pipeline.fetch import Cache
from citoyen_pipeline.inputs import InputError
from citoyen_pipeline.legal import LegalInput, content_text, latest_global_archive

TEXT = "LEGITEXT000000000001"
CODE = "LEGITEXT000000000002"
ROOT = "legi/global/code_et_TNC_en_vigueur/TNC_en_vigueur/JORF/TEXT/00/00/00/00/01/JORFTEXT000000000001"
CODE_ROOT = "legi/global/code_et_TNC_en_vigueur/code_en_vigueur/LEGI/TEXT/00/00/00/00/02/LEGITEXT000000000002"


def version(legi_id: str, title: str, nature: str) -> str:
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<TEXTE_VERSION><META><META_COMMUN><ID>{legi_id}</ID><NATURE>{nature}</NATURE></META_COMMUN>
<META_SPEC><META_TEXTE_VERSION><TITRE>{title}</TITRE><TITREFULL>{title} </TITREFULL><ETAT>VIGUEUR</ETAT>
</META_TEXTE_VERSION></META_SPEC></META></TEXTE_VERSION>"""


def struct(legi_id: str, links: str) -> str:
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<TEXTELR><META><META_COMMUN><ID>{legi_id}</ID></META_COMMUN></META><STRUCT>{links}</STRUCT></TEXTELR>"""


def section(section_id: str, title: str, links: str) -> str:
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<SECTION_TA><ID>{section_id}</ID><TITRE_TA>{title}</TITRE_TA><STRUCTURE_TA>{links}</STRUCTURE_TA></SECTION_TA>"""


def article(article_id: str, num: str, etat: str, start: str, content: str) -> str:
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<ARTICLE><META><META_COMMUN><ID>{article_id}</ID><NATURE>Article</NATURE></META_COMMUN><META_SPEC><META_ARTICLE>
<NUM>{num}</NUM><ETAT>{etat}</ETAT><DATE_DEBUT>{start}</DATE_DEBUT><DATE_FIN>2999-01-01</DATE_FIN>
</META_ARTICLE></META_SPEC></META><BLOC_TEXTUEL><CONTENU>{content}</CONTENU></BLOC_TEXTUEL></ARTICLE>"""


def link_art(article_id: str, num: str, etat: str, debut: str, fin: str) -> str:
    return (
        f'<LIEN_ART debut="{debut}" etat="{etat}" fin="{fin}" id="{article_id}" num="{num}" origine="LEGI"/>'
    )


def link_section(section_id: str, title: str, debut: str = "2000-01-01", fin: str = "2999-01-01") -> str:
    return (
        f'<LIEN_SECTION_TA cid="{section_id}" debut="{debut}" etat="VIGUEUR" fin="{fin}" id="{section_id}"'
        f' niv="1">{title}</LIEN_SECTION_TA>'
    )


def article_path(root: str, article_id: str) -> str:
    return f"{root}/article/LEGI/ARTI/00/00/00/00/00/{article_id}.xml"


def section_path(root: str, section_id: str) -> str:
    return f"{root}/section_ta/LEGI/SCTA/00/00/00/00/00/{section_id}.xml"


FILES = {
    f"{ROOT}/texte/version/{TEXT}.xml": version(TEXT, "Texte fictif de référence", "CONSTITUTION"),
    f"{ROOT}/texte/struct/{TEXT}.xml": struct(
        TEXT,
        link_art("LEGIARTI000000000010", "PREAMBULE", "MODIFIE", "2000-01-01", "2010-01-01")
        + link_art("LEGIARTI000000000011", "PREAMBULE", "VIGUEUR", "2010-01-01", "2999-01-01")
        + link_section("LEGISCTA000000000020", "Titre fictif I"),
    ),
    section_path(ROOT, "LEGISCTA000000000020"): section(
        "LEGISCTA000000000020",
        "Titre fictif I : Des règles   fictives",
        link_art("LEGIARTI000000000012", "1", "VIGUEUR", "2000-01-01", "2999-01-01")
        + link_art("LEGIARTI000000000013", "2", "ABROGE", "2000-01-01", "2005-01-01")
        + link_art("LEGIARTI000000000014", "3", "VIGUEUR", "2030-01-01", "2999-01-01")
        + link_section("LEGISCTA000000000021", "Chapitre fictif"),
    ),
    section_path(ROOT, "LEGISCTA000000000021"): section(
        "LEGISCTA000000000021",
        "Chapitre fictif",
        link_art("LEGIARTI000000000015", "4", "ABROGE_DIFF", "2000-01-01", "2030-01-01")
        + link_art("LEGIARTI000000000016", "4", "VIGUEUR_DIFF", "2030-01-01", "2999-01-01")
        + link_art("LEGIARTI000000000017", "5", "", "2999-01-01", "2999-01-01"),
    ),
    article_path(ROOT, "LEGIARTI000000000010"): article(
        "LEGIARTI000000000010", "PREAMBULE", "MODIFIE", "2000-01-01", "<p>Ancien texte fictif.</p>"
    ),
    article_path(ROOT, "LEGIARTI000000000011"): article(
        "LEGIARTI000000000011",
        "PREAMBULE",
        "VIGUEUR",
        "2010-01-01",
        "<p>Premier   paragraphe fictif.</p><p>Second paragraphe<br/>avec un saut de ligne.</p>",
    ),
    article_path(ROOT, "LEGIARTI000000000012"): article(
        "LEGIARTI000000000012", "1", "VIGUEUR", "2000-01-01", "<br/>   Règle fictive n° 1.<br/>"
    ),
    article_path(ROOT, "LEGIARTI000000000013"): article(
        "LEGIARTI000000000013", "2", "ABROGE", "2000-01-01", ""
    ),
    article_path(ROOT, "LEGIARTI000000000014"): article(
        "LEGIARTI000000000014", "3", "VIGUEUR", "2030-01-01", "<p>Future.</p>"
    ),
    article_path(ROOT, "LEGIARTI000000000015"): article(
        "LEGIARTI000000000015", "4", "VIGUEUR", "2000-01-01", "<p>Règle fictive n° 4.</p>"
    ),
    f"{CODE_ROOT}/texte/version/{CODE}.xml": version(CODE, "Code fictif", "CODE"),
    f"{CODE_ROOT}/texte/struct/{CODE}.xml": struct(
        CODE,
        link_section("LEGISCTA000000000030", "Partie fictive A")
        + link_section("LEGISCTA000000000031", "Partie fictive B"),
    ),
    section_path(CODE_ROOT, "LEGISCTA000000000030"): section(
        "LEGISCTA000000000030",
        "Partie fictive A",
        link_art("LEGIARTI000000000040", "L1", "VIGUEUR", "2000-01-01", "2999-01-01"),
    ),
    section_path(CODE_ROOT, "LEGISCTA000000000031"): section(
        "LEGISCTA000000000031",
        "Partie fictive B",
        link_art("LEGIARTI000000000041", "L2", "VIGUEUR", "2000-01-01", "2999-01-01"),
    ),
    article_path(CODE_ROOT, "LEGIARTI000000000040"): article(
        "LEGIARTI000000000040", "L1", "VIGUEUR", "2000-01-01", "<p>Article fictif L1.</p>"
    ),
    article_path(CODE_ROOT, "LEGIARTI000000000041"): article(
        "LEGIARTI000000000041", "L2", "VIGUEUR", "2000-01-01", "<p>Article fictif L2.</p>"
    ),
    "legi/global/autre/TEXT/LEGITEXT000000000099/texte/struct/LEGITEXT000000000099.xml": struct(
        "LEGITEXT000000000099", ""
    ),
}


@pytest.fixture
def cache(tmp_path) -> Cache:
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w:gz") as tar:
        for name, body in FILES.items():
            data = body.encode("utf-8")
            info = tarfile.TarInfo(name)
            info.size = len(data)
            tar.addfile(info, io.BytesIO(data))
    cache = Cache(tmp_path / "cache")
    cache.put(legal.ARCHIVE_KEY, "https://example.invalid/legi.tar.gz", buffer.getvalue(), "2026-10-01")
    legal.extract(cache.path(legal.ARCHIVE_KEY), cache.root / legal.EXTRACT_DIR, {TEXT, CODE})
    return cache


INPUTS = [
    LegalInput("texte-fictif", TEXT, 1, ()),
    LegalInput("code-fictif", CODE, 2, ("LEGISCTA000000000031",)),
]


def test_articles_in_force_in_reading_order_with_headings(cache):
    pack, review = legal.assemble(cache, INPUTS, date(2026, 10, 1))
    text = [a for a in pack.articles if a.text_id == "texte-fictif"]
    assert [(a.number, a.heading_path) for a in text] == [
        ("PREAMBULE", ()),
        ("1", ("Titre fictif I : Des règles fictives",)),
        ("4", ("Titre fictif I : Des règles fictives", "Chapitre fictif")),
    ]
    assert [a.ordinal for a in text] == [1, 2, 3]
    assert text[0].body == "Premier paragraphe fictif.\nSecond paragraphe\navec un saut de ligne."
    assert text[0].in_force_since == date(2010, 1, 1)
    assert text[1].body == "Règle fictive n° 1."
    # The version applying today ends on a date announced by the source.
    assert (text[2].in_force_until, text[0].in_force_until) == (date(2030, 1, 1), None)
    assert text[0].url == "https://www.legifrance.gouv.fr/loda/article_lc/LEGIARTI000000000011"
    assert review == {
        "legal texte-fictif: skipped links and future versions": [
            "LEGIARTI000000000014 (art. 3): version applying from 2030-01-01",
            "LEGIARTI000000000016 (art. 4): version applying from 2030-01-01",
        ]
    }
    first = pack.texts[0]
    assert (first.title, first.url) == (
        "Texte fictif de référence",
        f"https://www.legifrance.gouv.fr/loda/id/{TEXT}",
    )
    assert first.retrieved_on == date(2026, 10, 1)


def test_code_sections_selected_by_hand_and_code_links(cache):
    pack, _ = legal.assemble(cache, INPUTS, date(2026, 10, 1))
    code = [a for a in pack.articles if a.text_id == "code-fictif"]
    assert [(a.number, a.heading_path) for a in code] == [("L2", ("Partie fictive B",))]
    assert code[0].url == "https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000000000041"
    assert pack.texts[1].url == f"https://www.legifrance.gouv.fr/codes/texte_lc/{CODE}"


def test_unknown_section_is_refused(cache):
    with pytest.raises(InputError, match="sections not in force or not found"):
        legal.assemble(
            cache, [LegalInput("code-fictif", CODE, 1, ("LEGISCTA000000000099",))], date(2026, 10, 1)
        )


def test_text_missing_from_archive_is_refused(cache, tmp_path):
    with pytest.raises(RuntimeError, match="not found in the LEGI archive"):
        legal.extract(cache.path(legal.ARCHIVE_KEY), tmp_path / "x", {"LEGITEXT000000000077"})


def test_content_text_keeps_paragraphs_verbatim():
    import xml.etree.ElementTree as ET

    element = ET.fromstring("<CONTENU><p>Un <i>mot</i>  fictif.</p><br/><p>Deux : fin.</p></CONTENU>")
    assert content_text(element) == "Un mot fictif.\nDeux : fin."


def test_latest_global_archive_from_index():
    html = '<a href="LEGI_20261001-010101.tar.gz">x</a>'
    html += '<a href="Freemium_legi_global_20250713-140000.tar.gz">y</a>'
    html += '<a href="Freemium_legi_global_20260713-140000.tar.gz">z</a>'
    assert latest_global_archive(html).endswith("Freemium_legi_global_20260713-140000.tar.gz")


def test_legal_pack_version_follows_content(cache, tmp_path):
    pack, _ = legal.assemble(cache, INPUTS, date(2026, 10, 1))
    first = write_legal_pack(pack, tmp_path / "1.sqlite")
    pack.generated_on = date(2026, 10, 2)
    assert write_legal_pack(pack, tmp_path / "2.sqlite") == first
    db = sqlite3.connect(tmp_path / "2.sqlite")
    assert dict(db.execute("SELECT key, value FROM meta"))["pack_id"] == "legal"
    assert db.execute("SELECT COUNT(*) FROM legal_articles").fetchone()[0] == 4


def test_sections_are_selected_by_their_stable_cid(cache):
    # In LEGI a section keeps its `cid` while its `id` changes with each version.
    path = cache.root / legal.EXTRACT_DIR / CODE / "texte" / "struct" / f"{CODE}.xml"
    path.write_text(
        path.read_text(encoding="utf-8").replace('cid="LEGISCTA000000000031"', 'cid="LEGISCTA000000000131"'),
        encoding="utf-8",
    )
    pack, _ = legal.assemble(
        cache, [LegalInput("code-fictif", CODE, 1, ("LEGISCTA000000000131",))], date(2026, 10, 1)
    )
    assert [a.number for a in pack.articles] == ["L2"]


def _archive(path, files: dict[str, bytes]) -> None:
    with tarfile.open(path, "w:gz") as tar:
        for name, data in files.items():
            info = tarfile.TarInfo(name)
            info.size = len(data)
            tar.addfile(info, io.BytesIO(data))


def test_increments_after_the_global_archive_in_order():
    html = "".join(
        f'<a href="{n}">x</a>'
        for n in (
            "LEGI_20250712-211706.tar.gz",
            "Freemium_legi_global_20250713-140000.tar.gz",
            "LEGI_20250715-205701.tar.gz",
            "LEGI_20250713-205013.tar.gz",
        )
    )
    base = latest_global_archive(html)
    assert [u.rsplit("/", 1)[-1] for u in legal.increments_after(html, base)] == [
        "LEGI_20250713-205013.tar.gz",
        "LEGI_20250715-205701.tar.gz",
    ]
    assert legal.stamp_date(base) == date(2025, 7, 13)


def test_increment_updates_deletes_and_follows_a_moved_text(cache, tmp_path):
    target = cache.root / legal.EXTRACT_DIR
    directories = {TEXT: ROOT + "/", CODE: CODE_ROOT + "/"}
    moved = ROOT.replace("TNC_en_vigueur/TNC_en_vigueur", "TNC_non_vigueur/TNC_non_vigueur")
    stamp = "20261021-213023"
    changed = article("LEGIARTI000000000012", "1", "VIGUEUR", "2000-01-01", "<p>Règle fictive modifiée.</p>")
    increment = tmp_path / "LEGI_20261021-213023.tar.gz"
    _archive(
        increment,
        {
            f"{stamp}/liste_suppression_legi.dat": (
                article_path(CODE_ROOT, "LEGIARTI000000000040").removesuffix(".xml") + "\n"
            ).encode(),
            f"{stamp}/liste_suppression_legi_dossier.dat": f"{ROOT} D\n".encode(),
            # The text moves: all its files are published again under the new directory.
            **{
                f"{stamp}/{name.replace(ROOT, moved)}": body.encode()
                for name, body in FILES.items()
                if name.startswith(ROOT + "/")
            },
            f"{stamp}/{article_path(moved, 'LEGIARTI000000000012')}": changed.encode(),
        },
    )
    legal.apply_increment(increment, target, directories)
    assert directories[TEXT] == moved + "/"
    assert not (target / CODE / "article/LEGI/ARTI/00/00/00/00/00/LEGIARTI000000000040.xml").exists()
    pack, _ = legal.assemble(cache, INPUTS, date(2026, 10, 1))
    bodies = {a.id: a.body for a in pack.articles}
    assert bodies["LEGIARTI000000000012"] == "Règle fictive modifiée."
    assert "LEGIARTI000000000040" not in bodies
