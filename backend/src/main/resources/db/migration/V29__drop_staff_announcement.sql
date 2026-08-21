-- Staff announcements are no longer part of the product. Drop the join table first
-- so the parent table can be removed without CASCADE affecting unrelated objects.
DROP TABLE IF EXISTS staff_announcement_branch;
DROP TABLE IF EXISTS staff_announcement;
