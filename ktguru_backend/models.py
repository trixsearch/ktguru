"""SQLAlchemy declarative models (aligned with existing MySQL schema)."""
from __future__ import annotations

from datetime import datetime

from sqlalchemy import (
    DateTime,
    ForeignKey,
    Integer,
    String,
    Text,
    func,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class Document(Base):
    __tablename__ = "documents"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    filename: Mapped[str] = mapped_column(String(255), nullable=False)
    upload_date: Mapped[datetime] = mapped_column(
        DateTime,
        server_default=func.current_timestamp(),
    )

    chunks: Mapped[list[DocumentChunk]] = relationship(
        "DocumentChunk",
        back_populates="document",
        cascade="all, delete-orphan",
    )


class DocumentChunk(Base):
    __tablename__ = "document_chunks"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    document_id: Mapped[int] = mapped_column(
        ForeignKey("documents.id", ondelete="CASCADE"),
        nullable=False,
    )
    chunk_text: Mapped[str] = mapped_column(Text, nullable=False)
    chunk_index: Mapped[int | None] = mapped_column(Integer, nullable=True)

    document: Mapped[Document] = relationship("Document", back_populates="chunks")


class UnansweredQuestion(Base):
    __tablename__ = "unanswered_questions"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    question: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[str] = mapped_column(
        String(20),
        server_default="UNRESOLVED",
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        server_default=func.current_timestamp(),
    )


class Issue(Base):
    """Structured issue rows from CSV/Excel KT uploads (issues table)."""

    __tablename__ = "issues"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    document_id: Mapped[int | None] = mapped_column(
        ForeignKey("documents.id", ondelete="CASCADE"),
        nullable=True,
    )
    subject: Mapped[str] = mapped_column(Text, nullable=False)
    raised_by: Mapped[str] = mapped_column(String(512), nullable=False)
    raised_on: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    resolution_time: Mapped[str | None] = mapped_column(String(512), nullable=True)
    status: Mapped[str | None] = mapped_column(String(255), nullable=True)
    resolution: Mapped[str | None] = mapped_column(Text, nullable=True)
