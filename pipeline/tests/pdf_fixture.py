"""Builds small text PDFs for tests (no binary fixture in the repository)."""

from __future__ import annotations

import io

from pypdf import PdfReader, PdfWriter


def make_pdf(pages: list[list[str]], outline: list[tuple[str, int, int]] = ()) -> bytes:
    """One text line per entry; [outline] lists (title, page index, level), level 0 or 1."""
    objects: list[bytes | None] = []

    def add(body: bytes) -> int:
        objects.append(body)
        return len(objects)

    font = add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
    objects.append(None)
    pages_id = len(objects)
    page_ids = []
    for lines in pages:
        ops = [b"BT /F1 11 Tf 14 TL 72 760 Td"]
        for line in lines:
            escaped = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
            ops.append(b"(" + escaped.encode("cp1252") + b") Tj T*")
        ops.append(b"ET")
        stream = b"\n".join(ops)
        content = add(b"<< /Length %d >>\nstream\n" % len(stream) + stream + b"\nendstream")
        page_ids.append(
            add(
                b"<< /Type /Page /Parent %d 0 R /MediaBox [0 0 612 792] /Contents %d 0 R "
                b"/Resources << /Font << /F1 %d 0 R >> >> >>" % (pages_id, content, font)
            )
        )
    kids = b" ".join(b"%d 0 R" % i for i in page_ids)
    objects[pages_id - 1] = b"<< /Type /Pages /Kids [" + kids + b"] /Count %d >>" % len(page_ids)
    catalog = add(b"<< /Type /Catalog /Pages %d 0 R >>" % pages_id)
    out = io.BytesIO()
    out.write(b"%PDF-1.4\n")
    offsets = []
    for number, body in enumerate(objects, 1):
        offsets.append(out.tell())
        out.write(b"%d 0 obj\n" % number + body + b"\nendobj\n")
    xref = out.tell()
    out.write(b"xref\n0 %d\n0000000000 65535 f \n" % (len(objects) + 1))
    for offset in offsets:
        out.write(b"%010d 00000 n \n" % offset)
    out.write(
        b"trailer\n<< /Size %d /Root %d 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objects) + 1, catalog, xref)
    )
    data = out.getvalue()
    if not outline:
        return data
    writer = PdfWriter(clone_from=PdfReader(io.BytesIO(data)))
    parent = None
    for title, page, level in outline:
        item = writer.add_outline_item(title, page, parent=parent if level else None)
        if level == 0:
            parent = item
    buffer = io.BytesIO()
    writer.write(buffer)
    return buffer.getvalue()
