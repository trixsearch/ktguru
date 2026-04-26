"""Pydantic schemas for API I/O."""
from pydantic import BaseModel, Field


class AskRequest(BaseModel):
    query: str = Field(min_length=1, description="User question string")


class AskResponse(BaseModel):
    answer: str
    sources: list[str]
